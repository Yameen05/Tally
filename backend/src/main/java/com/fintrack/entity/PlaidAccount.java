package com.fintrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single account inside a Plaid Item (e.g. "Chase Checking", "Chase Sapphire credit card").
 */
@Entity
@Table(name = "plaid_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaidAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plaid_item_id", nullable = false)
    private PlaidItem plaidItem;

    @Column(name = "plaid_account_id", nullable = false, unique = true)
    private String accountId;

    @Column(nullable = false)
    private String name;

    /** Last four digits of the account number, e.g. "0000". */
    @Column(name = "account_mask")
    private String mask;

    /** depository, credit, loan, investment, etc. */
    @Column(name = "account_type")
    private String type;

    /** checking, savings, credit card, etc. */
    @Column(name = "account_subtype")
    private String subtype;

    @Column(name = "current_balance", precision = 14, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "available_balance", precision = 14, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "iso_currency_code")
    private String currencyCode;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
