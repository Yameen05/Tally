package com.fintrack.service;

import com.fintrack.dto.BudgetDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class InsightService {

    private static final String FALLBACK = "Unable to generate insights at this time. Please try again later.";
    private static final String RATE_LIMITED =
        "You've requested insights several times recently. Please wait a little while before trying again.";

    // Each OpenAI call costs money, so cache results per user/month and cap how
    // often a single user can trigger a fresh call.
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final long RATE_WINDOW_MS = 3_600_000; // 1 hour
    private static final int MAX_CALLS_PER_WINDOW = 20;

    private final WebClient.Builder webClientBuilder;
    private final BudgetService budgetService;

    private final Map<String, CacheEntry> insightCache = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Long>> callTimestamps = new ConcurrentHashMap<>();

    private WebClient webClient;

    @Value("${openai.api.key}")
    private String openAiKey;

    @Value("${openai.api.url}")
    private String openAiUrl;

    @Value("${openai.model}")
    private String model;

    public InsightService(WebClient.Builder webClientBuilder, BudgetService budgetService) {
        this.webClientBuilder = webClientBuilder;
        this.budgetService = budgetService;
    }

    @PostConstruct
    void init() {
        this.webClient = webClientBuilder.build();
    }

    public String getSpendingInsights(Long userId, int month, int year) {
        String cacheKey = userId + ":" + year + ":" + month;
        CacheEntry cached = insightCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.text();
        }

        if (!allowRequest(userId)) {
            log.warn("Insight rate limit reached for user {}", userId);
            return RATE_LIMITED;
        }

        BudgetDto.MonthlySummary summary = budgetService.getMonthlySummary(userId, month, year);
        String prompt = buildPrompt(summary);

        try {
            Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content",
                        "You are a helpful personal finance advisor. Be concise, practical, and encouraging. " +
                        "Give actionable advice in 3-5 bullet points. Use plain text, no markdown."),
                    Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 400,
                "temperature", 0.7
            );

            OpenAiResponse response = webClient.post()
                    .uri(openAiUrl)
                    .header("Authorization", "Bearer " + openAiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .block(Duration.ofSeconds(30));

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                log.warn("OpenAI returned empty or null response");
                return FALLBACK;
            }

            OpenAiMessage message = response.choices().get(0).message();
            if (message == null || message.content() == null) {
                log.warn("OpenAI response missing message content");
                return FALLBACK;
            }

            insightCache.put(cacheKey, new CacheEntry(message.content(), Instant.now().plus(CACHE_TTL)));
            return message.content();

        } catch (Exception e) {
            log.error("OpenAI API error: {}", e.getClass().getSimpleName());
            return FALLBACK;
        }
    }

    /** Sliding-window per-user limiter so a single account can't run up the OpenAI bill. */
    private boolean allowRequest(Long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = callTimestamps.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > RATE_WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_CALLS_PER_WINDOW) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private record CacheEntry(String text, Instant expiresAt) {}

    private String buildPrompt(BudgetDto.MonthlySummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Here is my financial summary for month %d/%d:\n\n",
                summary.getMonth(), summary.getYear()));
        sb.append(String.format("Total Income: $%.2f\n", summary.getTotalIncome()));
        sb.append(String.format("Total Expenses: $%.2f\n", summary.getTotalExpenses()));
        sb.append(String.format("Net Balance: $%.2f\n\n", summary.getNetBalance()));

        if (!summary.getExpensesByCategory().isEmpty()) {
            sb.append("Expenses by category:\n");
            summary.getExpensesByCategory().forEach((cat, amount) ->
                sb.append(String.format("  - %s: $%.2f\n", cat, amount)));
        }

        if (summary.getBudgets() != null && !summary.getBudgets().isEmpty()) {
            sb.append("\nBudget status:\n");
            summary.getBudgets().forEach(b -> sb.append(String.format(
                "  - %s: spent $%.2f of $%.2f (%.1f%%)\n",
                b.getCategory(), b.getSpentAmount(), b.getLimitAmount(), b.getPercentageUsed())));
        }

        sb.append("\nPlease give me specific, actionable advice to improve my finances this month.");
        return sb.toString();
    }

    // Package-private so tests can reference these types directly
    record OpenAiMessage(String content) {}
    record OpenAiChoice(OpenAiMessage message) {}
    record OpenAiResponse(List<OpenAiChoice> choices) {}
}
