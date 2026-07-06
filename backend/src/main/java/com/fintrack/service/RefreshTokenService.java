package com.fintrack.service;

import com.fintrack.entity.RefreshToken;
import com.fintrack.entity.User;
import com.fintrack.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${auth.refresh-expiration-ms:1209600000}")
    private long refreshExpirationMs;

    /** The raw token handed to the client plus its expiry; the raw value is never persisted. */
    public record IssuedToken(String rawToken, Duration maxAge) {
    }

    public record RotationResult(User user, IssuedToken newToken) {
    }

    @Transactional
    public IssuedToken issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .user(user)
                .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs)))
                .build());

        return new IssuedToken(rawToken, Duration.ofMillis(refreshExpirationMs));
    }

    /**
     * Validates the presented token, revokes it, and issues a replacement.
     * A revoked token being replayed means the token was stolen (either the
     * thief or the legitimate client is now using a stale copy), so the whole
     * family is revoked and the user must log in again.
     *
     * <p>The rejection is thrown from inside the transaction, and the family
     * revocation must survive it — hence noRollbackFor.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RotationResult rotate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> unauthorized("Invalid refresh token"));

        if (token.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllForUser(token.getUser().getId(), LocalDateTime.now());
            log.warn("Refresh token replay detected for user {}; revoked {} active tokens",
                    token.getUser().getId(), revoked);
            throw unauthorized("Refresh token no longer valid");
        }
        if (token.isExpired()) {
            throw unauthorized("Refresh token expired");
        }

        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);

        return new RotationResult(token.getUser(), issue(token.getUser()));
    }

    /** Kills every active session for a user, e.g. after a password reset. */
    @Transactional
    public void revokeAllForUser(Long userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now());
        if (revoked > 0) {
            log.info("Revoked {} refresh tokens for user {}", revoked, userId);
        }
    }

    /** Revokes the presented token if it exists. Safe to call with garbage input. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    /** Expired rows are dead weight; purge them daily. */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now());
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
