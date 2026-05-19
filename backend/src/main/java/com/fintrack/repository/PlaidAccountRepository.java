package com.fintrack.repository;

import com.fintrack.entity.PlaidAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaidAccountRepository extends JpaRepository<PlaidAccount, Long> {
    Optional<PlaidAccount> findByAccountId(String accountId);
    List<PlaidAccount> findByPlaidItem_UserId(Long userId);
}
