package com.fintrack.repository;

import com.fintrack.entity.Transaction;
import com.fintrack.entity.Transaction.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByPlaidTransactionId(String plaidTransactionId);

    void deleteByPlaidTransactionId(String plaidTransactionId);

    List<Transaction> findByUserIdOrderByDateDesc(Long userId);

    List<Transaction> findByUserIdAndTypeOrderByDateDesc(Long userId, TransactionType type);

    List<Transaction> findByUserIdAndCategoryOrderByDateDesc(Long userId, String category);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND MONTH(t.date) = :month AND YEAR(t.date) = :year ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndMonthAndYear(
        @Param("userId") Long userId,
        @Param("month") int month,
        @Param("year") int year
    );

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.date BETWEEN :startDate AND :endDate ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.type = :type AND MONTH(t.date) = :month AND YEAR(t.date) = :year")
    BigDecimal sumByUserIdAndTypeAndMonthAndYear(
        @Param("userId") Long userId,
        @Param("type") TransactionType type,
        @Param("month") int month,
        @Param("year") int year
    );

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.category = :category AND t.type = 'EXPENSE' " +
           "AND MONTH(t.date) = :month AND YEAR(t.date) = :year")
    BigDecimal sumExpenseByCategory(
        @Param("userId") Long userId,
        @Param("category") String category,
        @Param("month") int month,
        @Param("year") int year
    );
}
