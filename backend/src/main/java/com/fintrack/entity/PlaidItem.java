package com.fintrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a Plaid Item — a connection to a single financial institution for a user.
 * One user can have multiple Items (e.g. one for Chase, one for Bank of America).
 * Holds the (encrypted) access_token used for all API calls to that institution.
 */
@Entity
@Table(name = "plaid_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaidItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "plaid_item_id", nullable = false, unique = true)
    private String itemId;

    /** AES-GCM encrypted access_token. Never stored in plaintext. */
    @Column(name = "access_token_encrypted", nullable = false, length = 1024)
    private String accessTokenEncrypted;

    @Column(name = "institution_id")
    private String institutionId;

    @Column(name = "institution_name")
    private String institutionName;

    /** Cursor from /transactions/sync for incremental updates. */
    @Column(name = "sync_cursor", length = 1024)
    private String syncCursor;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "sync_error")
    private String syncError;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "plaidItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PlaidAccount> accounts;
}
