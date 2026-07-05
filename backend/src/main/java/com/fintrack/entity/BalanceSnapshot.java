package com.fintrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily net-worth snapshot computed from connected account balances after each
 * Plaid sync. One row per user per day; re-syncing the same day updates it.
 */
@Entity
@Table(name = "balance_snapshots", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "snapshot_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate date;

    @Column(name = "total_assets", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAssets;

    @Column(name = "total_liabilities", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalLiabilities;

    @Column(name = "net_worth", nullable = false, precision = 14, scale = 2)
    private BigDecimal netWorth;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
