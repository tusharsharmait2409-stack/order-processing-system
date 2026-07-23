package com.ecommerce.order.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration marker. The implementation is chosen by profile:
 * Caffeine (dev, in-process) and Redis (prod, shared) — both through Spring's
 * {@code CacheManager} abstraction, so the service uses the same annotations.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache of Order-by-id look-ups. */
    public static final String ORDERS_CACHE = "orders";
}
