package com.fintrack.repository;

import com.fintrack.entity.BalanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, Long> {

    Optional<BalanceSnapshot> findByUserIdAndDate(Long userId, LocalDate date);

    List<BalanceSnapshot> findByUserIdAndDateGreaterThanEqualOrderByDateAsc(Long userId, LocalDate from);
}
