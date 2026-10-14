package in.voltforge.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    public static final String CACHE_COMPONENTS = "components";
    public static final String CACHE_ADMIN_STATS = "adminStats";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(CACHE_COMPONENTS,
                Caffeine.newBuilder()
                        .maximumSize(2_000)
                        .expireAfterWrite(Duration.ofHours(1))
                        .recordStats()
                        .build());
        cacheManager.registerCustomCache(CACHE_ADMIN_STATS,
                Caffeine.newBuilder()
                        .maximumSize(10)
                        .expireAfterWrite(Duration.ofSeconds(30))
                        .recordStats()
                        .build());
        return cacheManager;
    }
}
