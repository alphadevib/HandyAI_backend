package com.handyai.build.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Who is on the site right now. Every open tab sends a heartbeat; a visitor counts as live until
 * {@link #LIVE_WINDOW} passes without one.
 *
 * <p>Kept in memory on purpose: presence is meaningless after a restart and changes too often to
 * be worth a database write per heartbeat. With more than one backend instance each would only
 * see its own visitors; move this to Redis at that point.
 */
@Service
public class PresenceService {

    static final Duration LIVE_WINDOW = Duration.ofSeconds(75);
    private static final int MAX_TRACKED = 100_000;
    private static final Pattern VISITOR_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    public record Visitor(Long userId, Instant lastSeen, String path) {
    }

    public record LiveCount(int total, int signedIn, int guests) {
    }

    private final Map<String, Visitor> visitors = new ConcurrentHashMap<>();

    /** Returns false for an id that does not look like one the frontend generates. */
    public boolean heartbeat(String visitorId, Long userId, String path) {
        if (visitorId == null || !VISITOR_ID.matcher(visitorId).matches()) {
            return false;
        }
        if (visitors.size() >= MAX_TRACKED && !visitors.containsKey(visitorId)) {
            prune();
            if (visitors.size() >= MAX_TRACKED) {
                return false;
            }
        }
        String cleanPath = path == null ? null : path.length() > 120 ? path.substring(0, 120) : path;
        visitors.put(visitorId, new Visitor(userId, Instant.now(), cleanPath));
        return true;
    }

    /** The signed-in user behind a visitor id, if that visitor has been seen recently. */
    public Long userFor(String visitorId) {
        if (visitorId == null) {
            return null;
        }
        Visitor visitor = visitors.get(visitorId);
        return visitor != null && isLive(visitor, Instant.now()) ? visitor.userId() : null;
    }

    public LiveCount live() {
        prune();
        Instant now = Instant.now();
        int total = 0;
        int signedIn = 0;
        for (Visitor visitor : visitors.values()) {
            if (isLive(visitor, now)) {
                total++;
                if (visitor.userId() != null) {
                    signedIn++;
                }
            }
        }
        return new LiveCount(total, signedIn, total - signedIn);
    }

    private void prune() {
        Instant cutoff = Instant.now().minus(LIVE_WINDOW.multipliedBy(2));
        visitors.values().removeIf(visitor -> visitor.lastSeen().isBefore(cutoff));
    }

    private static boolean isLive(Visitor visitor, Instant now) {
        return visitor.lastSeen().isAfter(now.minus(LIVE_WINDOW));
    }
}
