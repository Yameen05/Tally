package com.fintrack.service;

import com.fintrack.dto.BudgetDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class InsightService {

    private final WebClient.Builder webClientBuilder;
    private final BudgetService budgetService;

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
    private void init() {
        this.webClient = webClientBuilder.build();
    }

    public String getSpendingInsights(Long userId, int month, int year) {
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

            Map<String, Object> response = webClient.post()
                    .uri(openAiUrl)
                    .header("Authorization", "Bearer " + openAiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response == null) {
                log.warn("OpenAI returned null response");
                return "Unable to generate insights at this time. Please try again later.";
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("OpenAI returned empty choices");
                return "Unable to generate insights at this time. Please try again later.";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null || !message.containsKey("content")) {
                log.warn("OpenAI response missing message content");
                return "Unable to generate insights at this time. Please try again later.";
            }

            return (String) message.get("content");

        } catch (Exception e) {
            log.error("OpenAI API error: {}", e.getClass().getSimpleName());
            return "Unable to generate insights at this time. Please try again later.";
        }
    }

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
}
