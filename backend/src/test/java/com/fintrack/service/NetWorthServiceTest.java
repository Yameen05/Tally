package com.fintrack.service;

import com.fintrack.entity.BalanceSnapshot;
import com.fintrack.entity.PlaidAccount;
import com.fintrack.entity.User;
import com.fintrack.repository.BalanceSnapshotRepository;
import com.fintrack.repository.PlaidAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NetWorthServiceTest {

    @Mock private PlaidAccountRepository plaidAccountRepository;
    @Mock private BalanceSnapshotRepository balanceSnapshotRepository;

    @InjectMocks
    private NetWorthService netWorthService;

    private final User user = User.builder().id(1L).name("Y").email("y@e.com").password("x").build();

    private static PlaidAccount account(String type, String balance) {
        return PlaidAccount.builder().type(type).currentBalance(new BigDecimal(balance)).build();
    }

    @Test
    void snapshot_splitsAssetsAndLiabilities() {
        when(plaidAccountRepository.findByPlaidItem_UserId(1L)).thenReturn(List.of(
                account("depository", "2500.00"),
                account("investment", "10000.00"),
                account("credit", "740.25"),
                account("loan", "5000.00")
        ));
        when(balanceSnapshotRepository.findByUserIdAndDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        netWorthService.snapshot(user);

        verify(balanceSnapshotRepository).save(argThat(s ->
                s.getTotalAssets().compareTo(new BigDecimal("12500.00")) == 0
                        && s.getTotalLiabilities().compareTo(new BigDecimal("5740.25")) == 0
                        && s.getNetWorth().compareTo(new BigDecimal("6759.75")) == 0));
    }

    @Test
    void snapshot_updatesExistingRowForSameDay() {
        when(plaidAccountRepository.findByPlaidItem_UserId(1L))
                .thenReturn(List.of(account("depository", "100.00")));
        BalanceSnapshot existing = BalanceSnapshot.builder()
                .id(7L).user(user).date(LocalDate.now())
                .totalAssets(BigDecimal.ONE).totalLiabilities(BigDecimal.ZERO).netWorth(BigDecimal.ONE)
                .build();
        when(balanceSnapshotRepository.findByUserIdAndDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        netWorthService.snapshot(user);

        verify(balanceSnapshotRepository).save(argThat(s ->
                s.getId() == 7L && s.getNetWorth().compareTo(new BigDecimal("100.00")) == 0));
    }

    @Test
    void snapshot_skipsUsersWithNoAccounts() {
        when(plaidAccountRepository.findByPlaidItem_UserId(1L)).thenReturn(List.of());

        netWorthService.snapshot(user);

        verify(balanceSnapshotRepository, never()).save(any());
    }

    @Test
    void snapshot_ignoresNullBalances() {
        when(plaidAccountRepository.findByPlaidItem_UserId(1L)).thenReturn(List.of(
                account("depository", "50.00"),
                PlaidAccount.builder().type("depository").currentBalance(null).build()
        ));
        when(balanceSnapshotRepository.findByUserIdAndDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        netWorthService.snapshot(user);

        verify(balanceSnapshotRepository).save(argThat(s ->
                s.getNetWorth().compareTo(new BigDecimal("50.00")) == 0));
    }
}
