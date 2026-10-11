package com.handyai.build.security;

import com.handyai.build.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Each user's current token version, the switch behind "sign out everywhere": signing out bumps
 * the version in the database and every token carrying an older one stops working.
 *
 * <p>Looked up on authenticated requests, so versions are cached for {@link #FRESH_FOR} to avoid a
 * database round trip per request. This instance forgets a user the moment they sign out here;
 * with several instances, another one would notice within {@link #FRESH_FOR}.
 */
@Component
public class TokenVersions {

    static final Duration FRESH_FOR = Duration.ofSeconds(60);
    private static final int MAX_CACHED = 50_000;

    private record Cached(int version, Instant loadedAt) {
    }

    private final UserRepository users;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public TokenVersions(UserRepository users) {
        this.users = users;
    }

    /** The version a valid token for this user must carry; empty if the user no longer exists. */
    public OptionalInt current(long userId) {
        Instant now = Instant.now();
        Cached cached = cache.get(userId);
        if (cached != null && cached.loadedAt().plus(FRESH_FOR).isAfter(now)) {
            return OptionalInt.of(cached.version());
        }
        var version = users.findTokenVersion(userId);
        if (version.isEmpty()) {
            cache.remove(userId);
            return OptionalInt.empty();
        }
        if (cache.size() >= MAX_CACHED) {
            cache.clear();
        }
        cache.put(userId, new Cached(version.get(), now));
        return OptionalInt.of(version.get());
    }

    public void forget(long userId) {
        cache.remove(userId);
    }
}
