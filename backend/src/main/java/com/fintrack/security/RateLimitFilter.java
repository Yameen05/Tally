package com.fintrack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding-window rate limiter for /api/auth/** endpoints.
 * Limits each IP to MAX_REQUESTS per WINDOW_MS to prevent brute-force attacks.
 */
@Component
@Order(Integer.MIN_VALUE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    /** Configurable so tests (which fire many auth requests from one IP) can raise it. */
    @Value("${security.auth-rate-limit:10}")
    private int MAX_REQUESTS;

    private static final long WINDOW_MS = 60_000;
    private static final int TOO_MANY_REQUESTS_STATUS = 429;

    /**
     * Number of reverse proxies (e.g. nginx) in front of the app that append to
     * X-Forwarded-For. The real client is the hop our outermost trusted proxy
     * added, which is this many entries back from the end of the header.
     * Default 0 means "no trusted proxy" — use the direct socket peer, which a
     * client cannot spoof. Set to 1 when running behind a single nginx.
     */
    @Value("${security.trusted-proxy-count:0}")
    private int trustedProxyCount;

    private final Map<String, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();
    private final AtomicLong lastPrunedAt = new AtomicLong();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        long now = System.currentTimeMillis();
        pruneExpiredClients(now);

        Deque<Long> timestamps = requestTimestamps.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            pruneExpiredTimestamps(timestamps, now);
            if (timestamps.size() >= MAX_REQUESTS) {
                response.setStatus(TOO_MANY_REQUESTS_STATUS);
                response.setContentType("application/json");
                response.setHeader("Retry-After", String.valueOf(WINDOW_MS / 1000));
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
            timestamps.addLast(now);
        }

        chain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Only trust X-Forwarded-For when we know how many proxies sit in front of
        // us. The last entry is appended by the immediate proxy and is the real
        // peer; entries a client prepends are pushed further left and ignored.
        // Trusting the FIRST entry (as before) let a client spoof its IP and get a
        // fresh rate-limit bucket per request, defeating brute-force protection.
        if (trustedProxyCount > 0) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                int idx = hops.length - trustedProxyCount;
                if (idx < 0) idx = 0;
                String ip = hops[idx].trim();
                if (!ip.isBlank()) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private void pruneExpiredClients(long now) {
        long previousPrune = lastPrunedAt.get();
        if (now - previousPrune < WINDOW_MS || !lastPrunedAt.compareAndSet(previousPrune, now)) {
            return;
        }

        requestTimestamps.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                pruneExpiredTimestamps(timestamps, now);
                return timestamps.isEmpty();
            }
        });
    }

    private void pruneExpiredTimestamps(Deque<Long> timestamps, long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
            timestamps.pollFirst();
        }
    }
}
