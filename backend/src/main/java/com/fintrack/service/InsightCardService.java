package com.fintrack.service;

import com.fintrack.dto.BudgetDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, instantly-computable insight cards. These carry the numbers;
 * the LLM narrative (InsightService) is garnish on top. No external calls, so
 * they render immediately and identically every time.
 */
@Service
@RequiredArgsConstructor
public class InsightCardService {

    private final BudgetService budgetService;
    private final RecurringService recurringService;

    public enum Kind { TREND, WIN, WATCH, INFO }

    public record InsightCard(Kind kind, String title, String detail, BigDecimal deltaPct) {
        static InsightCard of(Kind kind, String title, String detail) {
            return new InsightCard(kind, title, detail, null);
        }
    }

    @Transactional(readOnly = true)
    public List<InsightCard> buildCards(Long userId, int month, int year) {
        List<InsightCard> cards = new ArrayList<>();

        BudgetDto.MonthlySummary current = budgetService.getMonthlySummary(userId, month, year);
        YearMonth previousMonth = YearMonth.of(year, month).minusMonths(1);
        BudgetDto.MonthlySummary previous = budgetService.getMonthlySummary(
                userId, previousMonth.getMonthValue(), previousMonth.getYear());

        addSpendingTrend(cards, current, previous);
        addBiggestCategorySwing(cards, current, previous);
        addSavingsRate(cards, current);
        addBudgetStatus(cards, current);
        addRecurringPriceHikes(cards, userId);
        return cards;
    }

    private void addSpendingTrend(List<InsightCard> cards,
                                  BudgetDto.MonthlySummary current, BudgetDto.MonthlySummary previous) {
        BigDecimal now = nvl(current.getTotalExpenses());
        BigDecimal before = nvl(previous.getTotalExpenses());
        if (before.signum() == 0) {
            if (now.signum() > 0) {
                cards.add(InsightCard.of(Kind.INFO, "First month of data",
                        String.format("You spent $%.2f this month. Next month you'll see how it compares.", now)));
            }
            return;
        }
        BigDecimal deltaPct = now.subtract(before)
                .multiply(BigDecimal.valueOf(100))
                .divide(before, 1, RoundingMode.HALF_UP);
        boolean up = deltaPct.signum() > 0;
        cards.add(new InsightCard(
                up ? Kind.WATCH : Kind.WIN,
                up ? "Spending is up vs last month" : "Spending is down vs last month",
                String.format("$%.2f this month vs $%.2f last month (%s%.1f%%).",
                        now, before, up ? "+" : "", deltaPct),
                deltaPct));
    }

    private void addBiggestCategorySwing(List<InsightCard> cards,
                                         BudgetDto.MonthlySummary current, BudgetDto.MonthlySummary previous) {
        Map<String, BigDecimal> now = current.getExpensesByCategory();
        Map<String, BigDecimal> before = previous.getExpensesByCategory();
        if (now == null || now.isEmpty() || before == null || before.isEmpty()) return;

        String biggestCategory = null;
        BigDecimal biggestIncrease = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : now.entrySet()) {
            BigDecimal increase = entry.getValue().subtract(before.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            if (increase.compareTo(biggestIncrease) > 0) {
                biggestIncrease = increase;
                biggestCategory = entry.getKey();
            }
        }
        // Only worth a card when the jump is material
        if (biggestCategory != null && biggestIncrease.compareTo(new BigDecimal("25")) > 0) {
            cards.add(InsightCard.of(Kind.TREND, biggestCategory + " grew the most",
                    String.format("%s is up $%.2f compared to last month — worth a look.",
                            biggestCategory, biggestIncrease)));
        }
    }

    private void addSavingsRate(List<InsightCard> cards, BudgetDto.MonthlySummary current) {
        BigDecimal income = nvl(current.getTotalIncome());
        BigDecimal expenses = nvl(current.getTotalExpenses());
        if (income.signum() <= 0) return;

        BigDecimal rate = income.subtract(expenses)
                .multiply(BigDecimal.valueOf(100))
                .divide(income, 1, RoundingMode.HALF_UP);
        if (rate.signum() < 0) {
            cards.add(new InsightCard(Kind.WATCH, "Spending exceeds income",
                    String.format("You spent $%.2f more than you earned this month.", expenses.subtract(income)),
                    rate));
        } else if (rate.compareTo(BigDecimal.valueOf(20)) >= 0) {
            cards.add(new InsightCard(Kind.WIN, "Healthy savings rate",
                    String.format("You kept %.1f%% of your income this month. The common target is 20%%.", rate),
                    rate));
        } else {
            cards.add(new InsightCard(Kind.INFO, "Savings rate",
                    String.format("You kept %.1f%% of your income this month. The common target is 20%%.", rate),
                    rate));
        }
    }

    private void addBudgetStatus(List<InsightCard> cards, BudgetDto.MonthlySummary current) {
        List<BudgetDto.Response> budgets = current.getBudgets();
        if (budgets == null || budgets.isEmpty()) return;

        List<String> over = budgets.stream()
                .filter(b -> b.getPercentageUsed() >= 100)
                .map(BudgetDto.Response::getCategory)
                .toList();
        if (!over.isEmpty()) {
            cards.add(InsightCard.of(Kind.WATCH, "Over budget: " + String.join(", ", over),
                    over.size() == 1
                            ? "One category blew past its limit this month."
                            : over.size() + " categories blew past their limits this month."));
        } else {
            cards.add(InsightCard.of(Kind.WIN, "All budgets on track",
                    "Every category is still under its monthly limit. Keep it up."));
        }
    }

    private void addRecurringPriceHikes(List<InsightCard> cards, Long userId) {
        recurringService.detect(userId).stream()
                .filter(item -> item.priceChangePct() != null && item.priceChangePct().signum() > 0)
                .max(Comparator.comparing(RecurringService.RecurringItem::priceChangePct))
                .ifPresent(item -> cards.add(new InsightCard(Kind.WATCH,
                        item.name() + " got more expensive",
                        String.format("The last charge was $%.2f, up %.1f%% from the previous one.",
                                item.lastAmount(), item.priceChangePct()),
                        item.priceChangePct())));
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
