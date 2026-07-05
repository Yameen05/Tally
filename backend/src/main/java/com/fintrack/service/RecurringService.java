package com.fintrack.service;

import com.fintrack.entity.Transaction;
import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects recurring expenses (subscriptions, bills, regular payments) from
 * transaction history. Purely computed — no extra state to keep in sync.
 *
 * A group of transactions is "recurring" when the same merchant shows up at
 * least {@link #MIN_OCCURRENCES} times at a roughly steady interval that maps
 * to a known cadence, and the series is still active (not obviously cancelled).
 */
@Service
@RequiredArgsConstructor
public class RecurringService {

    private static final int LOOKBACK_DAYS = 210; // ~7 months
    private static final int MIN_OCCURRENCES = 3;

    public enum Cadence {
        WEEKLY(7), BIWEEKLY(14), MONTHLY(30);

        final int days;

        Cadence(int days) {
            this.days = days;
        }

        static Optional<Cadence> fromMedianGap(double gap) {
            if (gap >= 5 && gap <= 9) return Optional.of(WEEKLY);
            if (gap >= 12 && gap <= 17) return Optional.of(BIWEEKLY);
            if (gap >= 25 && gap <= 36) return Optional.of(MONTHLY);
            return Optional.empty();
        }
    }

    public record RecurringItem(
            String name,
            String category,
            Cadence cadence,
            BigDecimal lastAmount,
            BigDecimal averageAmount,
            BigDecimal monthlyEstimate,
            LocalDate lastDate,
            LocalDate nextDueDate,
            int occurrences,
            /** Percent change of the most recent charge vs the one before it; null when unchanged. */
            BigDecimal priceChangePct) {
    }

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<RecurringItem> detect(Long userId) {
        LocalDate cutoff = LocalDate.now().minusDays(LOOKBACK_DAYS);
        List<Transaction> expenses = transactionRepository
                .findByUserIdAndDateRange(userId, cutoff, LocalDate.now())
                .stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> !Boolean.TRUE.equals(t.getPending()))
                .toList();

        Map<String, List<Transaction>> groups = expenses.stream()
                .collect(Collectors.groupingBy(RecurringService::merchantKey));

        List<RecurringItem> items = new ArrayList<>();
        for (List<Transaction> group : groups.values()) {
            analyzeGroup(group).ifPresent(items::add);
        }
        items.sort(Comparator.comparing(RecurringItem::monthlyEstimate).reversed());
        return items;
    }

    private Optional<RecurringItem> analyzeGroup(List<Transaction> group) {
        if (group.size() < MIN_OCCURRENCES) return Optional.empty();

        List<Transaction> sorted = group.stream()
                .sorted(Comparator.comparing(Transaction::getDate))
                .toList();

        // Interval consistency: the median gap must map to a cadence and no gap
        // may stray far from it (rules out one-off merchants hit sporadically).
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(sorted.get(i - 1).getDate(), sorted.get(i).getDate()));
        }
        double medianGap = median(gaps);
        Optional<Cadence> cadence = Cadence.fromMedianGap(medianGap);
        if (cadence.isEmpty()) return Optional.empty();
        long tolerance = Math.max(3, Math.round(medianGap * 0.35));
        boolean steady = gaps.stream().allMatch(g -> Math.abs(g - medianGap) <= tolerance);
        if (!steady) return Optional.empty();

        // Amount consistency: bills vary a little, groceries vary a lot.
        List<BigDecimal> amounts = sorted.stream().map(Transaction::getAmount).toList();
        BigDecimal avg = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP);
        boolean amountsConsistent = amounts.stream().allMatch(a ->
                a.subtract(avg).abs().compareTo(avg.multiply(new BigDecimal("0.35"))) <= 0);
        if (!amountsConsistent) return Optional.empty();

        // Still active? A series whose last charge is long overdue was cancelled.
        Transaction last = sorted.get(sorted.size() - 1);
        LocalDate nextDue = last.getDate().plusDays(Math.round(medianGap));
        if (nextDue.isBefore(LocalDate.now().minusDays(tolerance + cadence.get().days))) {
            return Optional.empty();
        }

        BigDecimal priceChangePct = null;
        if (sorted.size() >= 2) {
            BigDecimal previous = sorted.get(sorted.size() - 2).getAmount();
            if (previous.signum() > 0 && last.getAmount().compareTo(previous) != 0) {
                priceChangePct = last.getAmount().subtract(previous)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previous, 1, RoundingMode.HALF_UP);
            }
        }

        BigDecimal monthlyEstimate = switch (cadence.get()) {
            case WEEKLY -> avg.multiply(new BigDecimal("4.33"));
            case BIWEEKLY -> avg.multiply(new BigDecimal("2.17"));
            case MONTHLY -> avg;
        };

        return Optional.of(new RecurringItem(
                displayName(last),
                last.getCategory(),
                cadence.get(),
                last.getAmount(),
                avg,
                monthlyEstimate.setScale(2, RoundingMode.HALF_UP),
                last.getDate(),
                nextDue,
                sorted.size(),
                priceChangePct));
    }

    /** Normalizes merchant/description so "NETFLIX.COM 001" and "Netflix" group together. */
    static String merchantKey(Transaction t) {
        String raw = t.getMerchantName() != null && !t.getMerchantName().isBlank()
                ? t.getMerchantName()
                : t.getDescription();
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[0-9#*]+", " ")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String displayName(Transaction t) {
        return t.getMerchantName() != null && !t.getMerchantName().isBlank()
                ? t.getMerchantName()
                : t.getDescription();
    }

    private static double median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
