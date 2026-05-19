package com.fintrack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PlaidDto {

    @Data
    public static class ExchangeRequest {
        @NotBlank
        private String publicToken;
        private String institutionId;
        private String institutionName;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LinkTokenResponse {
        private String linkToken;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConnectedAccount {
        private Long id;
        private String accountId;
        private String name;
        private String mask;
        private String type;
        private String subtype;
        private BigDecimal currentBalance;
        private BigDecimal availableBalance;
        private String currencyCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConnectedItem {
        private Long id;
        private String institutionName;
        private LocalDateTime lastSyncedAt;
        private String syncError;
        private List<ConnectedAccount> accounts;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SyncResult {
        private int newTransactions;
        private int itemsSynced;
    }
}
