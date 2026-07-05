package com.fintrack.service;

import com.fintrack.entity.BalanceSnapshot;
import com.fintrack.entity.PlaidAccount;
import com.fintrack.entity.User;
import com.fintrack.repository.BalanceSnapshotRepository;
import com.fintrack.repository.PlaidAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NetWorthService {

    /** Plaid account types whose balances count against the user. */
    private static final Set<String> LIABILITY_TYPES = Set.of("credit", "loan");

    private final PlaidAccountRepository plaidAccountRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;

    public record NetWorthPoint(LocalDate date, BigDecimal assets, BigDecimal liabilities, BigDecimal netWorth) {
    }

    /**
     * Recomputes today's snapshot from current account balances. Called after
     * every Plaid sync, so each sync refreshes the same daily row.
     */
    @Transactional
    public void snapshot(User user) {
        List<PlaidAccount> accounts = plaidAccountRepository.findByPlaidItem_UserId(user.getId());
        if (accounts.isEmpty()) {
            return;
        }

        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        for (PlaidAccount account : accounts) {
            BigDecimal balance = account.getCurrentBalance();
            if (balance == null) continue;
            if (account.getType() != null && LIABILITY_TYPES.contains(account.getType())) {
                liabilities = liabilities.add(balance);
            } else {
                assets = assets.add(balance);
            }
        }

        LocalDate today = LocalDate.now();
        BalanceSnapshot snapshot = balanceSnapshotRepository
                .findByUserIdAndDate(user.getId(), today)
                .orElseGet(() -> BalanceSnapshot.builder().user(user).date(today).build());
        snapshot.setTotalAssets(assets);
        snapshot.setTotalLiabilities(liabilities);
        snapshot.setNetWorth(assets.subtract(liabilities));
        balanceSnapshotRepository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public List<NetWorthPoint> getHistory(Long userId, int days) {
        return balanceSnapshotRepository
                .findByUserIdAndDateGreaterThanEqualOrderByDateAsc(userId, LocalDate.now().minusDays(days))
                .stream()
                .map(s -> new NetWorthPoint(s.getDate(), s.getTotalAssets(), s.getTotalLiabilities(), s.getNetWorth()))
                .toList();
    }
}
