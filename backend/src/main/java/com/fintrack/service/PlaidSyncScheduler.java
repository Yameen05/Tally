package com.fintrack.service;

import com.fintrack.entity.PlaidItem;
import com.fintrack.repository.PlaidItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Background sync — runs every 6 hours to pull new transactions from Plaid
 * for every connected Item. Webhooks would be more efficient in production,
 * but polling works fine and keeps the architecture simple.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlaidSyncScheduler {

    private final PlaidService plaidService;
    private final PlaidItemRepository plaidItemRepository;

    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT1M")
    public void syncAllItems() {
        if (!plaidService.isConfigured()) return;

        List<PlaidItem> items = plaidItemRepository.findAll();
        if (items.isEmpty()) return;

        log.info("Plaid scheduled sync starting for {} item(s)", items.size());
        int success = 0, failures = 0;
        for (PlaidItem item : items) {
            try {
                plaidService.syncItem(item.getId());
                success++;
            } catch (Exception e) {
                failures++;
                log.error("Scheduled sync failed for item {}: {}", item.getItemId(), e.getMessage());
            }
        }
        log.info("Plaid scheduled sync complete: {} succeeded, {} failed", success, failures);
    }
}
