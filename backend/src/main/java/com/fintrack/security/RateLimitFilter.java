package com.fintrack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter for /api/auth/** endpoints.
 * Limits each IP to MAX_REQUESTS per WINDOW_MS to prevent brute-force attacks.
 */
@Component
@Order(Integer.MIN_VALUE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = requestTimestamps.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
            timestamps.addLast(now);
        }

        chain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
