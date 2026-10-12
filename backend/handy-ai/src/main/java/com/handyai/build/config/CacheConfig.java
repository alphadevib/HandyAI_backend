package com.handyai.build.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring's cache abstraction over the default in-memory {@code ConcurrentMapCacheManager}.
 *
 * <p>Every cache here is keyed by something with a small, fixed set of values (no key, a currency,
 * a tool slug), so the maps cannot grow without bound. The caching advice runs outside the
 * transaction ({@code order = HIGHEST_PRECEDENCE}), which makes {@code @CacheEvict} fire after the
 * commit: evicting before it could let a parallel read cache the old rows again.
 */
@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig {

    /** Categories with their tool counts; changes only when the catalogue is seeded. */
    public static final String CATEGORIES = "categories";

    /** The marketplace's price bands per currency; changes only when the catalogue is seeded. */
    public static final String PRICE_RANGES = "priceRanges";

    /** Public reviews per tool slug; evicted when a review is written or removed. */
    public static final String REVIEWS = "reviews";
}
