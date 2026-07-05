package com.fintrack.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void underThreshold_doesNotLock() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("a@b.com");
        }
        assertThatCode(() -> service.checkNotLocked("a@b.com")).doesNotThrowAnyException();
    }

    @Test
    void atThreshold_locksWith429() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("a@b.com");
        }
        assertThatThrownBy(() -> service.checkNotLocked("a@b.com"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void successResetsCounter() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("a@b.com");
        }
        service.recordSuccess("a@b.com");
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("a@b.com");
        }
        assertThatCode(() -> service.checkNotLocked("a@b.com")).doesNotThrowAnyException();
    }

    @Test
    void lockoutIsPerAccount() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("victim@b.com");
        }
        assertThatCode(() -> service.checkNotLocked("other@b.com")).doesNotThrowAnyException();
    }
}
