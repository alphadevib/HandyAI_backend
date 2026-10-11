package com.handyai.build.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing: after {@link #MAX_FAILURES} wrong passwords for one email, that
 * email cannot sign in for {@link #LOCKOUT}. Keyed by email rather than address, so a guesser
 * cannot get around it by switching networks. In memory, so it resets on restart and is per
 * instance, which is enough to make guessing a password impractical.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant firstFailure) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** Minutes left in the lockout for this email, or 0 when it may try again. */
    public long minutesLocked(String email) {
        Attempts current = attempts.get(email);
        if (current == null) {
            return 0;
        }
        Instant unlocksAt = current.firstFailure().plus(LOCKOUT);
        if (Instant.now().isAfter(unlocksAt)) {
            attempts.remove(email);
            return 0;
        }
        if (current.failures() < MAX_FAILURES) {
            return 0;
        }
        return Math.max(1, Duration.between(Instant.now(), unlocksAt).toMinutes() + 1);
    }

    public void recordFailure(String email) {
        attempts.compute(email, (key, current) -> current == null
                || Instant.now().isAfter(current.firstFailure().plus(LOCKOUT))
                ? new Attempts(1, Instant.now())
                : new Attempts(current.failures() + 1, current.firstFailure()));
        if (attempts.size() > 50_000) {
            Instant cutoff = Instant.now().minus(LOCKOUT);
            attempts.values().removeIf(entry -> entry.firstFailure().isBefore(cutoff));
        }
    }

    public void recordSuccess(String email) {
        attempts.remove(email);
    }
}
