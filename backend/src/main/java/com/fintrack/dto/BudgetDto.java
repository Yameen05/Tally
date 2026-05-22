package com.fintrack.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class BudgetDto {

    @Data
    public static class Request {
        @NotBlank(message = "Category is required")
        private String category;

        @NotNull @Positive
        private BigDecimal limitAmount;

        @NotNull @Min(1) @Max(12)
        private Integer month;

        @NotNull @Min(2000) @Max(2100)
        private Integer year;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long id;
        private String category;
        private BigDecimal limitAmount;
        private BigDecimal spentAmount;
        private BigDecimal remaining;
        private double percentageUsed;
        private Integer month;
        private Integer year;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MonthlySummary {
        private Integer month;
        private Integer year;
        private BigDecimal totalIncome;
        private BigDecimal totalExpenses;
        private BigDecimal netBalance;
        private Map<String, BigDecimal> expensesByCategory;
        private List<Response> budgets;
    }
}
