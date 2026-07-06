package com.fintrack.service;

import com.fintrack.entity.AccountToken;
import com.fintrack.entity.User;
import com.fintrack.repository.AccountTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * One-time tokens for email verification (48h) and password reset (1h).
 * Issuing a new token invalidates any outstanding ones for the same purpose,
 * so only the latest emailed link works.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    static final Duration VERIFY_TTL = Duration.ofHours(48);
    static final Duration RESET_TTL = Duration.ofHours(1);

    private final AccountTokenRepository accountTokenRepository;

    @Transactional
    public String issue(User user, AccountToken.Purpose purpose) {
        accountTokenRepository.invalidateAllForUser(user.getId(), purpose, LocalDateTime.now());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Duration ttl = purpose == AccountToken.Purpose.RESET_PASSWORD ? RESET_TTL : VERIFY_TTL;
        accountTokenRepository.save(AccountToken.builder()
                .tokenHash(RefreshTokenService.hash(rawToken))
                .user(user)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plus(ttl))
                .build());
        return rawToken;
    }

    /** Validates and burns the token, returning its user. 400 on anything invalid. */
    @Transactional
    public User consume(String rawToken, AccountToken.Purpose purpose) {
        AccountToken token = accountTokenRepository
                .findByTokenHashAndPurpose(RefreshTokenService.hash(rawToken), purpose)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This link is invalid or has already been used"));
        if (!token.isUsable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This link has expired or already been used. Request a new one.");
        }
        token.setUsedAt(LocalDateTime.now());
        accountTokenRepository.save(token);
        return token.getUser();
    }

    @Scheduled(cron = "0 40 4 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = accountTokenRepository.deleteExpiredBefore(LocalDateTime.now());
        if (removed > 0) {
            log.info("Purged {} expired account tokens", removed);
        }
    }
}
