package com.fintrack.service;

import com.fintrack.dto.TransactionDto;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.User;
import com.fintrack.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @InjectMocks private TransactionService transactionService;

    private User testUser;
    private Transaction testTx;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Yameen").email("yameen@example.com").build();
        testTx = Transaction.builder()
                .id(42L).user(testUser).description("Groceries")
                .amount(new BigDecimal("85.00")).type(Transaction.TransactionType.EXPENSE)
                .category("Food").date(LocalDate.of(2026, 5, 10)).build();
    }

    @Test
    void create_shouldSaveAndReturnResponse() {
        TransactionDto.Request req = new TransactionDto.Request();
        req.setDescription("Groceries");
        req.setAmount(new BigDecimal("85.00"));
        req.setType(Transaction.TransactionType.EXPENSE);
        req.setCategory("Food");
        req.setDate(LocalDate.of(2026, 5, 10));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTx);

        TransactionDto.Response resp = transactionService.create(req, testUser);

        assertThat(resp.getId()).isEqualTo(42L);
        assertThat(resp.getDescription()).isEqualTo("Groceries");
        assertThat(resp.getAmount()).isEqualByComparingTo("85.00");
        assertThat(resp.getType()).isEqualTo(Transaction.TransactionType.EXPENSE);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void getAll_shouldReturnPagedResults() {
        Page<Transaction> page = new PageImpl<>(List.of(testTx));
        when(transactionRepository.findByUserIdOrderByDateDesc(eq(1L), any(Pageable.class))).thenReturn(page);

        Page<TransactionDto.Response> result = transactionService.getAll(1L, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDescription()).isEqualTo("Groceries");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getByMonth_shouldReturnFilteredList() {
        when(transactionRepository.findByUserIdAndMonthAndYear(1L, 5, 2026))
                .thenReturn(List.of(testTx));

        List<TransactionDto.Response> result = transactionService.getByMonth(1L, 5, 2026);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("Food");
    }

    @Test
    void update_correctUser_shouldUpdateFields() {
        TransactionDto.Request req = new TransactionDto.Request();
        req.setDescription("Supermarket");
        req.setAmount(new BigDecimal("95.00"));
        req.setType(Transaction.TransactionType.EXPENSE);
        req.setCategory("Food");
        req.setDate(LocalDate.of(2026, 5, 11));
        when(transactionRepository.findById(42L)).thenReturn(Optional.of(testTx));
        when(transactionRepository.save(any())).thenReturn(testTx);

        transactionService.update(42L, req, 1L);

        verify(transactionRepository).save(argThat(t ->
                t.getDescription().equals("Supermarket")
                && t.getAmount().compareTo(new BigDecimal("95.00")) == 0));
    }

    @Test
    void update_wrongUser_shouldThrowForbidden() {
        when(transactionRepository.findById(42L)).thenReturn(Optional.of(testTx));

        assertThatThrownBy(() -> transactionService.update(42L, new TransactionDto.Request(), 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void delete_correctUser_shouldDelete() {
        when(transactionRepository.findById(42L)).thenReturn(Optional.of(testTx));

        transactionService.delete(42L, 1L);

        verify(transactionRepository).delete(testTx);
    }

    @Test
    void delete_wrongUser_shouldThrowForbidden() {
        when(transactionRepository.findById(42L)).thenReturn(Optional.of(testTx));

        assertThatThrownBy(() -> transactionService.delete(42L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void delete_notFound_shouldThrowNotFound() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.delete(999L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
