package com.fintrack.service;

import com.fintrack.entity.*;
import com.fintrack.repository.PlaidAccountRepository;
import com.fintrack.repository.PlaidItemRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Plaid integration: connect bank accounts via Link, exchange public tokens for
 * long-lived access tokens (encrypted at rest), and sync transactions incrementally
 * via /transactions/sync.
 *
 * Plaid sign convention: amount > 0 = money OUT (expense), amount < 0 = money IN (income).
 */
@Service
@Slf4j
public class PlaidService {

    private final WebClient plaidWebClient;
    private final EncryptionService encryptionService;
    private final PlaidItemRepository plaidItemRepository;
    private final PlaidAccountRepository plaidAccountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Value("${plaid.client-id}")
    private String clientId;

    @Value("${plaid.secret}")
    private String secret;

    @Value("${plaid.webhook-url}")
    private String webhookUrl;

    public PlaidService(@Qualifier("plaidWebClient") WebClient plaidWebClient,
                        EncryptionService encryptionService,
                        PlaidItemRepository plaidItemRepository,
                        PlaidAccountRepository plaidAccountRepository,
                        TransactionRepository transactionRepository,
                        UserRepository userRepository) {
        this.plaidWebClient = plaidWebClient;
        this.encryptionService = encryptionService;
        this.plaidItemRepository = plaidItemRepository;
        this.plaidAccountRepository = plaidAccountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && secret != null && !secret.isBlank();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Plaid is not configured. Set PLAID_CLIENT_ID and PLAID_SECRET env vars.");
        }
    }

    /**
     * Create a Link token that the frontend uses to initialize Plaid Link.
     * Each token is short-lived (~4 hours) and tied to one user.
     */
    public String createLinkToken(Long userId) {
        requireConfigured();
        Map<String, Object> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("secret", secret);
        body.put("client_name", "FinTrack");
        body.put("user", Map.of("client_user_id", String.valueOf(userId)));
        body.put("products", List.of("transactions"));
        body.put("country_codes", List.of("US"));
        body.put("language", "en");
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            body.put("webhook", webhookUrl);
        }

        Map<?, ?> response = call("/link/token/create", body);
        return (String) response.get("link_token");
    }

    /**
     * Exchange a public_token (from Link onSuccess) for a permanent access_token,
     * then fetch and persist the institution's accounts and historical transactions.
     */
    @Transactional
    public PlaidItem exchangePublicToken(Long userId, String publicToken, String institutionId, String institutionName) {
        requireConfigured();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // 1. Exchange the public token
        Map<?, ?> exchange = call("/item/public_token/exchange", Map.of(
                "client_id", clientId, "secret", secret, "public_token", publicToken
        ));
        String accessToken = (String) exchange.get("access_token");
        String itemId = (String) exchange.get("item_id");

        if (plaidItemRepository.findByItemId(itemId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is already connected.");
        }

        // 2. Persist the Item (with encrypted access token)
        PlaidItem item = PlaidItem.builder()
                .user(user)
                .itemId(itemId)
                .accessTokenEncrypted(encryptionService.encrypt(accessToken))
                .institutionId(institutionId)
                .institutionName(institutionName)
                .build();
        item = plaidItemRepository.save(item);

        // 3. Fetch and persist accounts
        syncAccounts(item, accessToken);

        // 4. Pull historical transactions (will run incrementally from then on)
        try {
            syncTransactionsInternal(item, accessToken);
        } catch (Exception e) {
            log.warn("Initial transaction sync failed for item {}: {}", itemId, e.getMessage());
            item.setSyncError(e.getMessage());
            plaidItemRepository.save(item);
        }

        return item;
    }

    @Transactional
    public void syncItem(Long plaidItemId) {
        PlaidItem item = plaidItemRepository.findById(plaidItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        String accessToken = encryptionService.decrypt(item.getAccessTokenEncrypted());
        syncAccounts(item, accessToken);
        syncTransactionsInternal(item, accessToken);
    }

    @Transactional
    public int syncAllForUser(Long userId) {
        if (!isConfigured()) return 0;
        List<PlaidItem> items = plaidItemRepository.findByUserId(userId);
        int totalNew = 0;
        for (PlaidItem item : items) {
            try {
                String accessToken = encryptionService.decrypt(item.getAccessTokenEncrypted());
                totalNew += syncTransactionsInternal(item, accessToken);
            } catch (Exception e) {
                log.error("Sync failed for item {}: {}", item.getItemId(), e.getMessage());
                item.setSyncError(e.getMessage());
                plaidItemRepository.save(item);
            }
        }
        return totalNew;
    }

    @Transactional
    public void disconnectItem(Long plaidItemId, Long userId) {
        PlaidItem item = plaidItemRepository.findById(plaidItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (!item.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // Notify Plaid (best-effort; we delete locally regardless)
        try {
            String accessToken = encryptionService.decrypt(item.getAccessTokenEncrypted());
            call("/item/remove", Map.of("client_id", clientId, "secret", secret, "access_token", accessToken));
        } catch (Exception e) {
            log.warn("Failed to remove Plaid item server-side: {}", e.getMessage());
        }

        plaidItemRepository.delete(item);
    }

    public List<PlaidItem> getItemsForUser(Long userId) {
        return plaidItemRepository.findByUserId(userId);
    }

    // ─── Internal: account sync ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void syncAccounts(PlaidItem item, String accessToken) {
        Map<?, ?> response = call("/accounts/get", Map.of(
                "client_id", clientId, "secret", secret, "access_token", accessToken
        ));
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) response.get("accounts");
        if (accounts == null) return;

        for (Map<String, Object> a : accounts) {
            String accountId = (String) a.get("account_id");
            PlaidAccount acc = plaidAccountRepository.findByAccountId(accountId)
                    .orElse(PlaidAccount.builder().plaidItem(item).accountId(accountId).build());

            acc.setName((String) a.get("name"));
            acc.setMask((String) a.get("mask"));
            acc.setType((String) a.get("type"));
            acc.setSubtype((String) a.get("subtype"));

            Map<String, Object> balances = (Map<String, Object>) a.get("balances");
            if (balances != null) {
                acc.setCurrentBalance(toBigDecimal(balances.get("current")));
                acc.setAvailableBalance(toBigDecimal(balances.get("available")));
                acc.setCurrencyCode((String) balances.get("iso_currency_code"));
            }
            plaidAccountRepository.save(acc);
        }
    }

    // ─── Internal: transaction sync ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private int syncTransactionsInternal(PlaidItem item, String accessToken) {
        int totalAdded = 0;
        String cursor = item.getSyncCursor();
        boolean hasMore = true;

        while (hasMore) {
            Map<String, Object> body = new HashMap<>();
            body.put("client_id", clientId);
            body.put("secret", secret);
            body.put("access_token", accessToken);
            if (cursor != null && !cursor.isBlank()) body.put("cursor", cursor);

            Map<String, Object> resp = call("/transactions/sync", body);

            List<Map<String, Object>> added = (List<Map<String, Object>>) resp.getOrDefault("added", List.of());
            List<Map<String, Object>> modified = (List<Map<String, Object>>) resp.getOrDefault("modified", List.of());
            List<Map<String, Object>> removed = (List<Map<String, Object>>) resp.getOrDefault("removed", List.of());

            for (Map<String, Object> tx : added) {
                upsertTransaction(item, tx);
                totalAdded++;
            }
            for (Map<String, Object> tx : modified) {
                upsertTransaction(item, tx);
            }
            for (Map<String, Object> tx : removed) {
                String txId = (String) tx.get("transaction_id");
                transactionRepository.findByPlaidTransactionId(txId).ifPresent(transactionRepository::delete);
            }

            cursor = (String) resp.get("next_cursor");
            Boolean more = (Boolean) resp.get("has_more");
            hasMore = Boolean.TRUE.equals(more);
        }

        item.setSyncCursor(cursor);
        item.setLastSyncedAt(LocalDateTime.now());
        item.setSyncError(null);
        plaidItemRepository.save(item);
        return totalAdded;
    }

    @SuppressWarnings("unchecked")
    private void upsertTransaction(PlaidItem item, Map<String, Object> tx) {
        String plaidTxId = (String) tx.get("transaction_id");
        BigDecimal plaidAmount = toBigDecimal(tx.get("amount"));
        if (plaidAmount == null) return;

        // Plaid: positive = money out (expense), negative = money in (income)
        Transaction.TransactionType type = plaidAmount.signum() >= 0
                ? Transaction.TransactionType.EXPENSE
                : Transaction.TransactionType.INCOME;
        BigDecimal amount = plaidAmount.abs();

        String name = (String) tx.get("name");
        String merchant = (String) tx.get("merchant_name");
        String dateStr = (String) tx.get("date");
        LocalDate date = (dateStr != null) ? LocalDate.parse(dateStr) : LocalDate.now();
        Boolean pending = Boolean.TRUE.equals(tx.get("pending"));
        String accountId = (String) tx.get("account_id");

        Map<String, Object> pfc = (Map<String, Object>) tx.get("personal_finance_category");
        String primaryCategory = pfc != null ? (String) pfc.get("primary") : null;
        String mappedCategory = mapPlaidCategory(primaryCategory, type);

        Transaction existing = transactionRepository.findByPlaidTransactionId(plaidTxId).orElse(null);
        if (existing != null) {
            existing.setDescription(displayName(name, merchant));
            existing.setMerchantName(merchant);
            existing.setAmount(amount);
            existing.setType(type);
            existing.setCategory(mappedCategory);
            existing.setDate(date);
            existing.setPending(pending);
            existing.setPlaidAccountId(accountId);
            transactionRepository.save(existing);
        } else {
            Transaction t = Transaction.builder()
                    .user(item.getUser())
                    .description(displayName(name, merchant))
                    .merchantName(merchant)
                    .amount(amount)
                    .type(type)
                    .category(mappedCategory)
                    .date(date)
                    .pending(pending)
                    .plaidTransactionId(plaidTxId)
                    .plaidAccountId(accountId)
                    .build();
            transactionRepository.save(t);
        }
    }

    private String displayName(String name, String merchant) {
        if (merchant != null && !merchant.isBlank()) return merchant;
        if (name != null && !name.isBlank()) return name;
        return "Transaction";
    }

    // ─── Category mapping: Plaid taxonomy → our internal categories ─────────

    private static final Map<String, String> CATEGORY_MAP = Map.ofEntries(
            Map.entry("FOOD_AND_DRINK", "Food"),
            Map.entry("GENERAL_MERCHANDISE", "Shopping"),
            Map.entry("HOME_IMPROVEMENT", "Housing"),
            Map.entry("RENT_AND_UTILITIES", "Utilities"),
            Map.entry("LOAN_PAYMENTS", "Other"),
            Map.entry("MEDICAL", "Healthcare"),
            Map.entry("TRANSPORTATION", "Transport"),
            Map.entry("TRAVEL", "Entertainment"),
            Map.entry("ENTERTAINMENT", "Entertainment"),
            Map.entry("PERSONAL_CARE", "Shopping"),
            Map.entry("GENERAL_SERVICES", "Other"),
            Map.entry("GOVERNMENT_AND_NON_PROFIT", "Other"),
            Map.entry("TRANSFER_IN", "Other"),
            Map.entry("TRANSFER_OUT", "Other"),
            Map.entry("BANK_FEES", "Other"),
            Map.entry("INCOME", "Salary")
    );

    private String mapPlaidCategory(String plaidPrimary, Transaction.TransactionType type) {
        if (plaidPrimary == null) return type == Transaction.TransactionType.INCOME ? "Salary" : "Other";
        return CATEGORY_MAP.getOrDefault(plaidPrimary, type == Transaction.TransactionType.INCOME ? "Salary" : "Other");
    }

    // ─── HTTP helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String path, Map<String, Object> body) {
        try {
            Map<String, Object> resp = plaidWebClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));
            if (resp == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from Plaid");
            }
            return resp;
        } catch (WebClientResponseException e) {
            String errMsg = "Plaid API error: " + e.getStatusCode();
            try {
                Map<?, ?> err = e.getResponseBodyAs(Map.class);
                if (err != null && err.get("error_message") != null) {
                    errMsg = (String) err.get("error_message");
                }
            } catch (Exception ignored) {}
            log.error("Plaid call to {} failed: {}", path, errMsg);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, errMsg);
        }
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}
