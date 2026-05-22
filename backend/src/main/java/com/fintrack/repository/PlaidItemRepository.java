package com.fintrack.repository;

import com.fintrack.entity.PlaidItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaidItemRepository extends JpaRepository<PlaidItem, Long> {
    @EntityGraph(attributePaths = "accounts")
    List<PlaidItem> findByUserId(Long userId);
    Optional<PlaidItem> findByItemId(String itemId);
    long countByUserId(Long userId);
}
