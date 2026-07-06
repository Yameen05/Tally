package com.fintrack.service;

import com.fintrack.dto.BudgetDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightCardServiceTest {

    @Mock private BudgetService budgetService;
    @Mock private RecurringService recurringService;

    @InjectMocks
    private InsightCardService insightCardService;

    private static BudgetDto.MonthlySummary summary(String income, String expenses,
                                                    Map<String, BigDecimal> byCategory,
                                                    List<BudgetDto.Response> budgets) {
        BudgetDto.MonthlySummary s = new BudgetDto.MonthlySummary();
        s.setMonth(6);
        s.setYear(2026);
        s.setTotalIncome(new BigDecimal(income));
        s.setTotalExpenses(new BigDecimal(expenses));
        s.setNetBalance(new BigDecimal(income).subtract(new BigDecimal(expenses)));
        s.setExpensesByCategory(byCategory);
        s.setBudgets(budgets);
        return s;
    }

    private void stubMonths(BudgetDto.MonthlySummary current, BudgetDto.MonthlySummary previous) {
        YearMonth now = YearMonth.of(2026, 6);
        YearMonth prev = now.minusMonths(1);
        when(budgetService.getMonthlySummary(1L, now.getMonthValue(), now.getYear())).thenReturn(current);
        when(budgetService.getMonthlySummary(1L, prev.getMonthValue(), prev.getYear())).thenReturn(previous);
        when(recurringService.detect(1L)).thenReturn(List.of());
    }

    @Test
    void spendingDown_producesWinTrendCard() {
        stubMonths(
                summary("3000", "1500", Map.of("Food", new BigDecimal("500")), List.of()),
                summary("3000", "2000", Map.of("Food", new BigDecimal("800")), List.of()));

        List<InsightCardService.InsightCard> cards = insightCardService.buildCards(1L, 6, 2026);

        assertThat(cards).anySatisfy(card -> {
            assertThat(card.title()).contains("down vs last month");
            assertThat(card.kind()).isEqualTo(InsightCardService.Kind.WIN);
            assertThat(card.deltaPct()).isEqualByComparingTo(new BigDecimal("-25.0"));
        });
    }

    @Test
    void categorySwing_flagsBiggestIncrease() {
        stubMonths(
                summary("3000", "2100", Map.of(
                        "Food", new BigDecimal("600"),
                        "Entertainment", new BigDecimal("900")), List.of()),
                summary("3000", "1500", Map.of(
                        "Food", new BigDecimal("580"),
                        "Entertainment", new BigDecimal("400")), List.of()));

        List<InsightCardService.InsightCard> cards = insightCardService.buildCards(1L, 6, 2026);

        assertThat(cards).anySatisfy(card ->
                assertThat(card.title()).isEqualTo("Entertainment grew the most"));
    }

    @Test
    void savingsRate_overTwentyPercent_isAWin() {
        stubMonths(
                summary("4000", "2800", Map.of(), List.of()),
                summary("0", "0", Map.of(), List.of()));

        List<InsightCardService.InsightCard> cards = insightCardService.buildCards(1L, 6, 2026);

        assertThat(cards).anySatisfy(card -> {
            assertThat(card.title()).isEqualTo("Healthy savings rate");
            assertThat(card.deltaPct()).isEqualByComparingTo(new BigDecimal("30.0"));
        });
    }

    @Test
    void overBudgetCategories_produceWatchCard() {
        BudgetDto.Response overBudget = BudgetDto.Response.builder()
                .category("Food").limitAmount(new BigDecimal("300"))
                .spentAmount(new BigDecimal("450")).percentageUsed(150).build();
        stubMonths(
                summary("3000", "450", Map.of("Food", new BigDecimal("450")), List.of(overBudget)),
                summary("0", "0", Map.of(), List.of()));

        List<InsightCardService.InsightCard> cards = insightCardService.buildCards(1L, 6, 2026);

        assertThat(cards).anySatisfy(card -> {
            assertThat(card.kind()).isEqualTo(InsightCardService.Kind.WATCH);
            assertThat(card.title()).contains("Over budget: Food");
        });
    }

    @Test
    void recurringPriceHike_producesWatchCard() {
        stubMonths(
                summary("3000", "1000", Map.of(), List.of()),
                summary("3000", "1000", Map.of(), List.of()));
        when(recurringService.detect(1L)).thenReturn(List.of(new RecurringService.RecurringItem(
                "Spotify", "Entertainment", RecurringService.Cadence.MONTHLY,
                new BigDecimal("11.99"), new BigDecimal("10.66"), new BigDecimal("10.66"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(29), 3,
                new BigDecimal("20.0"))));

        List<InsightCardService.InsightCard> cards = insightCardService.buildCards(1L, 6, 2026);

        assertThat(cards).anySatisfy(card ->
                assertThat(card.title()).isEqualTo("Spotify got more expensive"));
    }
}
