package com.fintrack.service;

import com.fintrack.entity.AccountToken;
import com.fintrack.entity.User;
import com.fintrack.repository.AccountTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountTokenServiceTest {

    @Mock private AccountTokenRepository accountTokenRepository;

    @InjectMocks
    private AccountTokenService accountTokenService;

    private final User user = User.builder().id(1L).name("Y").email("y@e.com").password("x").build();

    @Test
    void issue_invalidatesPreviousTokensAndStoresHash() {
        String raw = accountTokenService.issue(user, AccountToken.Purpose.RESET_PASSWORD);

        verify(accountTokenRepository).invalidateAllForUser(eq(1L),
                eq(AccountToken.Purpose.RESET_PASSWORD), any(LocalDateTime.class));
        verify(accountTokenRepository).save(argThat(saved ->
                !saved.getTokenHash().equals(raw) && saved.getPurpose() == AccountToken.Purpose.RESET_PASSWORD));
        assertThat(raw).isNotBlank();
    }

    @Test
    void consume_validToken_burnsItAndReturnsUser() {
        AccountToken token = AccountToken.builder()
                .id(5L).user(user).purpose(AccountToken.Purpose.VERIFY_EMAIL)
                .tokenHash(RefreshTokenService.hash("raw"))
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(accountTokenRepository.findByTokenHashAndPurpose(
                RefreshTokenService.hash("raw"), AccountToken.Purpose.VERIFY_EMAIL))
                .thenReturn(Optional.of(token));

        User result = accountTokenService.consume("raw", AccountToken.Purpose.VERIFY_EMAIL);

        assertThat(result).isEqualTo(user);
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void consume_usedToken_throws400() {
        AccountToken token = AccountToken.builder()
                .id(5L).user(user).purpose(AccountToken.Purpose.RESET_PASSWORD)
                .tokenHash(RefreshTokenService.hash("raw"))
                .expiresAt(LocalDateTime.now().plusHours(1))
                .usedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(accountTokenRepository.findByTokenHashAndPurpose(anyString(), any()))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> accountTokenService.consume("raw", AccountToken.Purpose.RESET_PASSWORD))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void consume_wrongPurpose_throws400() {
        when(accountTokenRepository.findByTokenHashAndPurpose(anyString(),
                eq(AccountToken.Purpose.VERIFY_EMAIL))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountTokenService.consume("raw", AccountToken.Purpose.VERIFY_EMAIL))
                .isInstanceOf(ResponseStatusException.class);
    }
}
