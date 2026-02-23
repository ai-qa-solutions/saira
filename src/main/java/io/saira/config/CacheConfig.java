package io.saira.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/** Конфигурация кэширования на базе Caffeine. */
@Configuration
public class CacheConfig {

    /** TTL кэша в минутах. */
    private static final long CACHE_TTL_MINUTES = 5;

    /** Максимальное количество записей в кэше. */
    private static final long CACHE_MAX_SIZE = 500;

    /** Менеджер кэшей Caffeine с 5-минутным TTL и лимитом 500 записей. */
    @Bean
    public CacheManager cacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(CACHE_MAX_SIZE));
        return cacheManager;
    }
}
