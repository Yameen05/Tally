package com.fintrack.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.dto.PlaidDto;
import com.fintrack.entity.PlaidItem;
import com.fintrack.entity.User;
import com.fintrack.security.PlaidWebhookVerifier;
import com.fintrack.service.AuthService;
import com.fintrack.service.PlaidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plaid")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Plaid", description = "Connect bank accounts and sync transactions automatically")
@SecurityRequirement(name = "bearerAuth")
public class PlaidController {

    private final PlaidService plaidService;
    private final AuthService authService;
    private final PlaidWebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    @GetMapping("/status")
    @Operation(summary = "Check whether Plaid is configured on this server")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("configured", plaidService.isConfigured()));
    }

    @PostMapping("/link-token")
    @Operation(summary = "Create a Plaid Link token for the current user")
    public ResponseEntity<PlaidDto.LinkTokenResponse> createLinkToken(Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        String token = plaidService.createLinkToken(user.getId());
        return ResponseEntity.ok(PlaidDto.LinkTokenResponse.builder().linkToken(token).build());
    }

    @PostMapping("/exchange")
    @Operation(summary = "Exchange a public token from Plaid Link for a permanent connection")
    public ResponseEntity<PlaidDto.ConnectedItem> exchange(
            @Valid @RequestBody PlaidDto.ExchangeRequest req,
            Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        PlaidItem item = plaidService.exchangePublicToken(
                user.getId(), req.getPublicToken(), req.getInstitutionId(), req.getInstitutionName());
        return ResponseEntity.ok(toDto(item));
    }

    @GetMapping("/items")
    @Operation(summary = "List the user's connected banks and accounts")
    public ResponseEntity<List<PlaidDto.ConnectedItem>> listItems(Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        List<PlaidDto.ConnectedItem> result = plaidService.getItemsForUser(user.getId())
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync all connected banks for the current user")
    public ResponseEntity<PlaidDto.SyncResult> sync(Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        int items = plaidService.getItemsForUser(user.getId()).size();
        int newTx = plaidService.syncAllForUser(user.getId());
        return ResponseEntity.ok(PlaidDto.SyncResult.builder()
                .itemsSynced(items).newTransactions(newTx).build());
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "Disconnect a bank")
    public ResponseEntity<Void> disconnect(@PathVariable Long id, Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        plaidService.disconnectItem(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/webhook")
    @Operation(summary = "Receive Plaid webhook events", security = {})
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "Plaid-Verification", required = false) String verification,
            @RequestBody(required = false) byte[] rawBody) {

        // Verify the request genuinely came from Plaid before doing any work.
        // The raw bytes must be hashed exactly as received, so we parse JSON only
        // after the signature and body-hash checks pass.
        if (rawBody == null || !webhookVerifier.verify(verification, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            log.warn("Malformed Plaid webhook body: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        String webhookType = (String) payload.get("webhook_type");
        String webhookCode = (String) payload.get("webhook_code");
        String itemId = (String) payload.get("item_id");

        if ("TRANSACTIONS".equals(webhookType) && "SYNC_UPDATES_AVAILABLE".equals(webhookCode)
                && itemId != null) {
            try {
                plaidService.syncByPlaidItemId(itemId);
            } catch (Exception e) {
                // Log but always return 200 so Plaid does not retry indefinitely
                log.error("Webhook-triggered sync failed for item {}: {}", itemId, e.getMessage());
            }
        }
        return ResponseEntity.ok().build();
    }

    // ─── helpers ───
    private PlaidDto.ConnectedItem toDto(PlaidItem item) {
        List<PlaidDto.ConnectedAccount> accounts = (item.getAccounts() == null) ? List.of()
                : item.getAccounts().stream().map(a -> PlaidDto.ConnectedAccount.builder()
                        .id(a.getId())
                        .accountId(a.getAccountId())
                        .name(a.getName())
                        .mask(a.getMask())
                        .type(a.getType())
                        .subtype(a.getSubtype())
                        .currentBalance(a.getCurrentBalance())
                        .availableBalance(a.getAvailableBalance())
                        .currencyCode(a.getCurrencyCode())
                        .build()).toList();
        return PlaidDto.ConnectedItem.builder()
                .id(item.getId())
                .institutionName(item.getInstitutionName())
                .lastSyncedAt(item.getLastSyncedAt())
                .syncError(item.getSyncError())
                .accounts(accounts)
                .build();
    }
}
