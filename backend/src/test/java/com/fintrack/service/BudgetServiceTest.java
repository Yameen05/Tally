package com.fintrack.service;

import com.fintrack.dto.BudgetDto;
import com.fintrack.entity.Budget;
import com.fintrack.entity.User;
import com.fintrack.repository.BudgetRepository;
import com.fintrack.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private BudgetService budgetService;

    private User testUser;
    private Budget testBudget;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Yameen").email("yameen@example.com").build();

        testBudget = Budget.builder()
                .id(10L)
                .user(testUser)
                .category("Food")
                .limitAmount(new BigDecimal("500.00"))
                .month(5)
                .year(2026)
                .build();
    }

    @Test
    void createOrUpdate_newBudget_shouldSaveAndReturnResponse() {
        when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(1L, "Food", 5, 2026))
                .thenReturn(Optional.empty());
        when(budgetRepository.save(any(Budget.class))).thenReturn(testBudget);
        when(transactionRepository.sumExpenseByCategory(1L, "Food", 5, 2026))
                .thenReturn(new BigDecimal("120.00"));

        BudgetDto.Request request = new BudgetDto.Request();
        request.setCategory("Food");
        request.setLimitAmount(new BigDecimal("500.00"));
        request.setMonth(5);
        request.setYear(2026);

        BudgetDto.Response response = budgetService.createOrUpdate(request, testUser);

        assertThat(response.getCategory()).isEqualTo("Food");
        assertThat(response.getLimitAmount()).isEqualByComparingTo("500.00");
        assertThat(response.getSpentAmount()).isEqualByComparingTo("120.00");
        verify(budgetRepository).save(any(Budget.class));
    }

    @Test
    void createOrUpdate_existingBudget_shouldUpdateLimit() {
        when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(1L, "Food", 5, 2026))
                .thenReturn(Optional.of(testBudget));
        when(budgetRepository.save(any(Budget.class))).thenReturn(testBudget);
        when(transactionRepository.sumExpenseByCategory(any(), any(), anyInt(), anyInt()))
                .thenReturn(BigDecimal.ZERO);

        BudgetDto.Request request = new BudgetDto.Request();
        request.setCategory("Food");
        request.setLimitAmount(new BigDecimal("600.00"));
        request.setMonth(5);
        request.setYear(2026);

        budgetService.createOrUpdate(request, testUser);

        verify(budgetRepository).save(argThat(b -> b.getLimitAmount().compareTo(new BigDecimal("600.00")) == 0));
    }

    @Test
    void delete_withCorrectUser_shouldSucceed() {
        when(budgetRepository.findById(10L)).thenReturn(Optional.of(testBudget));

        budgetService.delete(10L, 1L);

        verify(budgetRepository).delete(testBudget);
    }

    @Test
    void delete_withWrongUser_shouldThrowForbidden() {
        when(budgetRepository.findById(10L)).thenReturn(Optional.of(testBudget));

        assertThatThrownBy(() -> budgetService.delete(10L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void delete_nonExistentBudget_shouldThrowNotFound() {
        when(budgetRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.delete(999L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getByMonth_shouldReturnResponseList() {
        when(budgetRepository.findByUserIdAndMonthAndYear(1L, 5, 2026))
                .thenReturn(List.of(testBudget));
        when(transactionRepository.sumExpenseByCategory(any(), any(), anyInt(), anyInt()))
                .thenReturn(new BigDecimal("80.00"));

        List<BudgetDto.Response> results = budgetService.getByMonth(1L, 5, 2026);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSpentAmount()).isEqualByComparingTo("80.00");
        assertThat(results.get(0).getPercentageUsed()).isEqualTo(16.0);
    }
}
