package com.fintrack.service;

import com.fintrack.entity.PlaidItem;
import com.fintrack.repository.PlaidItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaidSyncSchedulerTest {

    @Mock private PlaidService plaidService;
    @Mock private PlaidItemRepository plaidItemRepository;
    @InjectMocks private PlaidSyncScheduler scheduler;

    @Test
    void syncAllItems_whenNotConfigured_doesNothing() {
        when(plaidService.isConfigured()).thenReturn(false);

        scheduler.syncAllItems();

        verify(plaidItemRepository, never()).findAll();
        verify(plaidService, never()).syncItem(anyLong());
    }

    @Test
    void syncAllItems_whenNoItems_doesNothing() {
        when(plaidService.isConfigured()).thenReturn(true);
        when(plaidItemRepository.findAll()).thenReturn(List.of());

        scheduler.syncAllItems();

        verify(plaidService, never()).syncItem(anyLong());
    }

    @Test
    void syncAllItems_syncsEachItem() {
        PlaidItem item1 = PlaidItem.builder().id(1L).itemId("item_aaa").build();
        PlaidItem item2 = PlaidItem.builder().id(2L).itemId("item_bbb").build();
        when(plaidService.isConfigured()).thenReturn(true);
        when(plaidItemRepository.findAll()).thenReturn(List.of(item1, item2));

        scheduler.syncAllItems();

        verify(plaidService).syncItem(1L);
        verify(plaidService).syncItem(2L);
    }

    @Test
    void syncAllItems_continuesAfterOneFailure() {
        PlaidItem item1 = PlaidItem.builder().id(1L).itemId("item_aaa").build();
        PlaidItem item2 = PlaidItem.builder().id(2L).itemId("item_bbb").build();
        when(plaidService.isConfigured()).thenReturn(true);
        when(plaidItemRepository.findAll()).thenReturn(List.of(item1, item2));
        doThrow(new RuntimeException("Plaid error")).when(plaidService).syncItem(1L);

        scheduler.syncAllItems(); // must not propagate the exception

        verify(plaidService).syncItem(2L);
    }
}
