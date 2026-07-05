package com.fintrack.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-account lockout after repeated failed logins. Complements the per-IP
 * RateLimitFilter: a distributed credential-stuffing attack rotates IPs but
 * still hammers the same account.
 *
 * <p>In-memory like the rate limiter — resets on restart and is per-instance,
 * which matches the app's current single-instance deployment.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant firstFailureAt, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();

    /** Throws 429 if the account is currently locked out. Call before authenticating. */
    public void checkNotLocked(String email) {
        Attempts attempts = attemptsByEmail.get(email);
        if (attempts != null && attempts.lockedUntil() != null) {
            long secondsLeft = Duration.between(Instant.now(), attempts.lockedUntil()).toSeconds();
            if (secondsLeft > 0) {
                long minutes = Math.max(1, (secondsLeft + 59) / 60);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed login attempts. Try again in " + minutes
                                + (minutes == 1 ? " minute." : " minutes."));
            }
            attemptsByEmail.remove(email);
        }
    }

    public void recordFailure(String email) {
        attemptsByEmail.compute(email, (k, current) -> {
            Instant now = Instant.now();
            if (current == null || current.firstFailureAt().plus(FAILURE_WINDOW).isBefore(now)) {
                return new Attempts(1, now, null);
            }
            int failures = current.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCKOUT) : null;
            return new Attempts(failures, current.firstFailureAt(), lockedUntil);
        });
    }

    public void recordSuccess(String email) {
        attemptsByEmail.remove(email);
    }
}
