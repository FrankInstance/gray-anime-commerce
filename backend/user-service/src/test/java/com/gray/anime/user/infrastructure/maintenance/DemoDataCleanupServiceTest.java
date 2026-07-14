package com.gray.anime.user.infrastructure.maintenance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoDataCleanupServiceTest {
    private DemoDataCleanupRepository repository;
    private DemoInventoryCacheSynchronizer cacheSynchronizer;
    private DemoDataCleanupService service;

    @BeforeEach
    void setUp() {
        repository = mock(DemoDataCleanupRepository.class);
        cacheSynchronizer = mock(DemoInventoryCacheSynchronizer.class);
        service = new DemoDataCleanupService(repository, cacheSynchronizer);
    }

    @Test
    void initializesTheSixMonthScheduleWithoutPurgingEarly() {
        when(repository.claimDueRun()).thenReturn(Optional.empty());

        assertThat(service.runIfDue()).isFalse();

        verify(repository).initializeSchedule();
        verify(repository, never()).purgeAndComplete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void purgesAClaimedRunAndRefreshesInventoryCache() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 14, 4, 0);
        when(repository.claimDueRun()).thenReturn(Optional.of(cutoff));
        when(repository.purgeAndComplete(cutoff)).thenReturn(new DemoDataCleanupSummary(12, 30, 18));

        assertThat(service.runIfDue()).isTrue();

        verify(repository).purgeAndComplete(cutoff);
        verify(cacheSynchronizer).refresh();
        verify(repository, never()).markFailed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void marksTheJobForRetryWhenTheTransactionFails() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 14, 4, 0);
        when(repository.claimDueRun()).thenReturn(Optional.of(cutoff));
        when(repository.purgeAndComplete(cutoff)).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(service.runIfDue()).isFalse();

        verify(repository).markFailed("database unavailable");
        verify(cacheSynchronizer, never()).refresh();
    }

    @Test
    void startupContinuesWhenTheScheduleTableCannotBeChecked() {
        doThrow(new IllegalStateException("database unavailable")).when(repository).initializeSchedule();

        assertThat(service.runIfDue()).isFalse();

        verify(repository, never()).claimDueRun();
        verify(repository, never()).markFailed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cacheFailureDoesNotRepeatAnAlreadyCommittedCleanup() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 14, 4, 0);
        when(repository.claimDueRun()).thenReturn(Optional.of(cutoff));
        when(repository.purgeAndComplete(cutoff)).thenReturn(new DemoDataCleanupSummary(1, 1, 1));
        doThrow(new IllegalStateException("redis unavailable")).when(cacheSynchronizer).refresh();

        assertThat(service.runIfDue()).isTrue();

        verify(repository, never()).markFailed(org.mockito.ArgumentMatchers.any());
    }
}
