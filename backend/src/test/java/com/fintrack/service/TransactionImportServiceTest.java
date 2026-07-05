package com.fintrack.service;

import com.fintrack.entity.Transaction;
import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionImportService importService;

    private final User user = User.builder().id(1L).name("Y").email("y@e.com").password("x").build();

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importsRoundTripOfOwnExportFormat() {
        String content = """
                Date,Description,Category,Type,Amount,Notes
                2026-06-01,"Grocery run",Food,EXPENSE,85.00,"weekly shop"
                2026-06-02,Salary,Salary,INCOME,3000.00,
                """;
        when(transactionRepository.existsByUserIdAndDateAndDescriptionAndAmountAndType(
                anyLong(), any(), any(), any(), any())).thenReturn(false);

        TransactionImportService.ImportResult result = importService.importCsv(csv(content), user);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.duplicates()).isZero();
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void toleratesDollarSignsCommasAndUsDates() {
        String content = """
                Date,Description,Category,Type,Amount,Notes
                6/15/2026,Rent,Housing,expense,"$1,850.00",
                """;
        when(transactionRepository.existsByUserIdAndDateAndDescriptionAndAmountAndType(
                anyLong(), any(), any(), any(), any())).thenReturn(false);

        TransactionImportService.ImportResult result = importService.importCsv(csv(content), user);

        assertThat(result.imported()).isEqualTo(1);
        verify(transactionRepository).save(argThat(t ->
                t.getDate().equals(LocalDate.of(2026, 6, 15))
                        && t.getAmount().compareTo(new BigDecimal("1850.00")) == 0
                        && t.getType() == TransactionType.EXPENSE));
    }

    @Test
    void skipsDuplicatesAndReportsThem() {
        String content = """
                Date,Description,Category,Type,Amount,Notes
                2026-06-01,Coffee,Food,EXPENSE,4.50,
                """;
        when(transactionRepository.existsByUserIdAndDateAndDescriptionAndAmountAndType(
                anyLong(), any(), any(), any(), any())).thenReturn(true);

        TransactionImportService.ImportResult result = importService.importCsv(csv(content), user);

        assertThat(result.imported()).isZero();
        assertThat(result.duplicates()).isEqualTo(1);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void collectsRowErrorsWithoutAbortingTheImport() {
        String content = """
                Date,Description,Category,Type,Amount,Notes
                not-a-date,Coffee,Food,EXPENSE,4.50,
                2026-06-01,Coffee,Food,MAYBE,4.50,
                2026-06-02,Tea,Food,EXPENSE,2.00,
                """;
        when(transactionRepository.existsByUserIdAndDateAndDescriptionAndAmountAndType(
                anyLong(), any(), any(), any(), any())).thenReturn(false);

        TransactionImportService.ImportResult result = importService.importCsv(csv(content), user);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0).message()).contains("date");
        assertThat(result.errors().get(1).message()).contains("INCOME or EXPENSE");
    }

    @Test
    void rejectsCsvMissingRequiredColumns() {
        String content = """
                When,What
                2026-06-01,Coffee
                """;

        assertThatThrownBy(() -> importService.importCsv(csv(content), user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Date, Description, Amount, and Type");
    }

    @Test
    void defaultsMissingCategoryToOther() {
        String content = """
                Date,Description,Type,Amount
                2026-06-01,Mystery charge,EXPENSE,9.99
                """;
        when(transactionRepository.existsByUserIdAndDateAndDescriptionAndAmountAndType(
                anyLong(), any(), any(), any(), any())).thenReturn(false);

        importService.importCsv(csv(content), user);

        verify(transactionRepository).save(argThat(t -> t.getCategory().equals("Other")));
    }
}
