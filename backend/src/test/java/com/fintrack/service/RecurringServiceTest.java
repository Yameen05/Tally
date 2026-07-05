package com.fintrack.service;

import com.fintrack.entity.Transaction;
import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringServiceTest {

    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private RecurringService recurringService;

    private static Transaction tx(String description, String merchant, String amount, LocalDate date) {
        return Transaction.builder()
                .description(description)
                .merchantName(merchant)
                .amount(new BigDecimal(amount))
                .type(TransactionType.EXPENSE)
                .category("Entertainment")
                .date(date)
                .pending(false)
                .build();
    }

    private void stubTransactions(List<Transaction> txs) {
        when(transactionRepository.findByUserIdAndDateRange(eq(1L), any(), any())).thenReturn(txs);
    }

    @Test
    void detect_findsMonthlySubscriptionWithSteadyAmount() {
        LocalDate today = LocalDate.now();
        stubTransactions(List.of(
                tx("NETFLIX.COM 0231", "Netflix", "15.49", today.minusDays(95)),
                tx("NETFLIX.COM 8812", "Netflix", "15.49", today.minusDays(64)),
                tx("NETFLIX.COM 1177", "Netflix", "15.49", today.minusDays(34)),
                tx("NETFLIX.COM 9310", "Netflix", "15.49", today.minusDays(3))
        ));

        List<RecurringService.RecurringItem> items = recurringService.detect(1L);

        assertThat(items).hasSize(1);
        RecurringService.RecurringItem netflix = items.get(0);
        assertThat(netflix.name()).isEqualTo("Netflix");
        assertThat(netflix.cadence()).isEqualTo(RecurringService.Cadence.MONTHLY);
        assertThat(netflix.occurrences()).isEqualTo(4);
        assertThat(netflix.priceChangePct()).isNull();
        assertThat(netflix.nextDueDate()).isAfter(today.minusDays(10));
    }

    @Test
    void detect_flagsPriceIncrease() {
        LocalDate today = LocalDate.now();
        stubTransactions(List.of(
                tx("Spotify", "Spotify", "9.99", today.minusDays(92)),
                tx("Spotify", "Spotify", "9.99", today.minusDays(61)),
                tx("Spotify", "Spotify", "11.99", today.minusDays(31)),
                tx("Spotify", "Spotify", "11.99", today.minusDays(1))
        ));

        List<RecurringService.RecurringItem> items = recurringService.detect(1L);

        // Last two charges are equal, so no change is flagged on the latest one
        assertThat(items).hasSize(1);
        assertThat(items.get(0).priceChangePct()).isNull();

        // Now the increase is the most recent event
        stubTransactions(List.of(
                tx("Spotify", "Spotify", "9.99", today.minusDays(61)),
                tx("Spotify", "Spotify", "9.99", today.minusDays(31)),
                tx("Spotify", "Spotify", "11.99", today.minusDays(1))
        ));
        items = recurringService.detect(1L);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).priceChangePct()).isEqualByComparingTo(new BigDecimal("20.0"));
    }

    @Test
    void detect_ignoresIrregularSpending() {
        LocalDate today = LocalDate.now();
        stubTransactions(List.of(
                tx("Corner Store", "Corner Store", "8.20", today.minusDays(88)),
                tx("Corner Store", "Corner Store", "42.75", today.minusDays(71)),
                tx("Corner Store", "Corner Store", "3.10", today.minusDays(30)),
                tx("Corner Store", "Corner Store", "19.99", today.minusDays(2))
        ));

        assertThat(recurringService.detect(1L)).isEmpty();
    }

    @Test
    void detect_ignoresCancelledSubscription() {
        LocalDate today = LocalDate.now();
        stubTransactions(List.of(
                tx("Hulu", "Hulu", "12.99", today.minusDays(200)),
                tx("Hulu", "Hulu", "12.99", today.minusDays(170)),
                tx("Hulu", "Hulu", "12.99", today.minusDays(140))
        ));

        assertThat(recurringService.detect(1L)).isEmpty();
    }

    @Test
    void detect_findsWeeklyPattern() {
        LocalDate today = LocalDate.now();
        List<Transaction> txs = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            txs.add(tx("Cleaning service", null, "60.00", today.minusDays(i * 7L + 1)));
        }
        stubTransactions(txs);

        List<RecurringService.RecurringItem> items = recurringService.detect(1L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).cadence()).isEqualTo(RecurringService.Cadence.WEEKLY);
        // ~$60/week ≈ $260/month
        assertThat(items.get(0).monthlyEstimate()).isEqualByComparingTo(new BigDecimal("259.80"));
    }

    @Test
    void merchantKey_normalizesNoise() {
        Transaction a = tx("NETFLIX.COM 0231", null, "15.49", LocalDate.now());
        Transaction b = tx("Netflix com", null, "15.49", LocalDate.now());
        assertThat(RecurringService.merchantKey(a)).isEqualTo(RecurringService.merchantKey(b));
    }
}
