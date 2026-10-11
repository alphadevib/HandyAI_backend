package com.handyai.build.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers keys for a while, to count something once per window ("this visitor already clicked
 * this tool"). In memory and bounded: when full it drops expired keys, and if that is not enough,
 * everything, which at worst counts a few repeats.
 */
public class RecentKeys {

    private final int capacity;
    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    public RecentKeys(int capacity) {
        this.capacity = capacity;
    }

    /** True the first time a key is seen within {@code window}; false for repeats inside it. */
    public boolean firstIn(String key, Duration window) {
        Instant now = Instant.now();
        if (seen.size() >= capacity) {
            seen.values().removeIf(at -> at.plus(window).isBefore(now));
            if (seen.size() >= capacity) {
                seen.clear();
            }
        }
        Instant previous = seen.get(key);
        if (previous != null && previous.plus(window).isAfter(now)) {
            return false;
        }
        seen.put(key, now);
        return true;
    }
}
