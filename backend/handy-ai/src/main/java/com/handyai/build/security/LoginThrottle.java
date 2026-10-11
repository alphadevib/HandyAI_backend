package com.handyai.build.security;

import com.handyai.build.domain.LoginAttempt;
import com.handyai.build.repository.LoginAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slows down password guessing with two limits over a {@link #LOCKOUT} window:
 * <ul>
 *   <li>{@link #MAX_FAILURES} wrong passwords for one email <em>from one address</em>. Keying by
 *   address as well means a stranger cannot lock someone else (say the admin) out of their own
 *   account just by typing their email wrongly a few times.</li>
 *   <li>{@link #MAX_FAILURES_PER_ADDRESS} wrong passwords from one address across all emails, so
 *   spraying many accounts from one machine is cut off too.</li>
 * </ul>
 * Attempts live in the database, so a restart does not hand an attacker a fresh allowance. Writes
 * run in their own transaction because sign-in itself is read-only.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 30;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final int MAX_KEY_LENGTH = 220;

    private final LoginAttemptRepository attempts;

    public LoginThrottle(LoginAttemptRepository attempts) {
        this.attempts = attempts;
    }

    /** Minutes left before this email may try again from this address, or 0. */
    @Transactional(readOnly = true)
    public long minutesLocked(String email, String address) {
        return Math.max(minutesLocked(accountKey(email, address), MAX_FAILURES),
                minutesLocked(addressKey(address), MAX_FAILURES_PER_ADDRESS));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, String address) {
        Instant now = Instant.now();
        attempts.deleteStartedBefore(now.minus(LOCKOUT));
        bump(accountKey(email, address), now);
        bump(addressKey(address), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String email, String address) {
        attempts.deleteById(accountKey(email, address));
    }

    private long minutesLocked(String key, int limit) {
        LoginAttempt attempt = attempts.findById(key).orElse(null);
        if (attempt == null || attempt.getFailures() < limit) {
            return 0;
        }
        Instant unlocksAt = attempt.getFirstFailure().plus(LOCKOUT);
        Instant now = Instant.now();
        if (!now.isBefore(unlocksAt)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, unlocksAt).toMinutes() + 1);
    }

    private void bump(String key, Instant now) {
        LoginAttempt attempt = attempts.findById(key).orElse(null);
        if (attempt == null || !now.isBefore(attempt.getFirstFailure().plus(LOCKOUT))) {
            attempts.save(new LoginAttempt(key, now));
        } else {
            attempt.addFailure();
        }
    }

    private static String accountKey(String email, String address) {
        return clip(email + "|" + address);
    }

    private static String addressKey(String address) {
        return clip("address|" + address);
    }

    private static String clip(String key) {
        return key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH) : key;
    }
}
