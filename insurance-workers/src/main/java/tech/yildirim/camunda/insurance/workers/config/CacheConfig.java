package tech.yildirim.camunda.insurance.workers.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache configuration for the application.
 *
 * <p>Defines a Caffeine-backed {@link CacheManager} and a reusable {@link Caffeine} builder.
 * The configuration creates a cache named "policyValidity" which the {@code PolicyService}
 * uses via {@code @Cacheable} to avoid repeated remote calls for the same policy number.
 *
 * <p>Adjust TTL and maximum size below to match your environment and expected load.
 */
@Configuration
public class CacheConfig {

  /**
   * Provides a pre-configured Caffeine builder used by the cache manager.
   *
   * @return a Caffeine builder with default TTL and maximum size
   */
  @Bean
  public Caffeine<Object, Object> caffeineConfig() {
    return Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .maximumSize(10_000);
  }

  /**
   * CacheManager that registers caches and uses the Caffeine builder.
   *
   * @param caffeine the Caffeine builder bean
   * @return a configured CacheManager
   */
  @Bean
  public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager(
        "policyValidity", "customer", "autoClaim", "healthClaim", "homeClaim");
    cacheManager.setCaffeine(caffeine);
    return cacheManager;
  }
}
