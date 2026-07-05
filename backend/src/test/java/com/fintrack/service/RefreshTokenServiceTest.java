package com.fintrack.service;

import com.fintrack.entity.RefreshToken;
import com.fintrack.entity.User;
import com.fintrack.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 1_209_600_000L);
        user = User.builder().id(1L).name("Yameen").email("yameen@example.com").password("hash").build();
    }

    @Test
    void issue_returnsRawTokenAndStoresOnlyHash() {
        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(user);

        assertThat(issued.rawToken()).isNotBlank();
        verify(refreshTokenRepository).save(argThat(saved ->
                saved.getTokenHash().equals(RefreshTokenService.hash(issued.rawToken()))
                        && !saved.getTokenHash().equals(issued.rawToken())
                        && saved.getExpiresAt().isAfter(LocalDateTime.now())));
    }

    @Test
    void rotate_validToken_revokesOldAndIssuesNew() {
        RefreshToken stored = RefreshToken.builder()
                .id(10L).user(user)
                .tokenHash(RefreshTokenService.hash("old-token"))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("old-token")))
                .thenReturn(Optional.of(stored));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("old-token");

        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(result.user()).isEqualTo(user);
        assertThat(result.newToken().rawToken()).isNotBlank();
        // one save revoking the old token, one save persisting the new one
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void rotate_unknownToken_throwsUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("nope"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void rotate_replayedRevokedToken_revokesWholeFamily() {
        RefreshToken revoked = RefreshToken.builder()
                .id(10L).user(user)
                .tokenHash(RefreshTokenService.hash("stolen"))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revokedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("stolen")))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("stolen"))
                .isInstanceOf(ResponseStatusException.class);

        verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void rotate_expiredToken_throwsUnauthorized() {
        RefreshToken expired = RefreshToken.builder()
                .id(10L).user(user)
                .tokenHash(RefreshTokenService.hash("expired"))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("expired")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void revoke_unknownToken_isNoOp() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        refreshTokenService.revoke("garbage");

        verify(refreshTokenRepository, never()).save(any());
    }
}
