package ai.visitorflow.demo.data.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableCaching
@EnableConfigurationProperties(ExperimentCacheProperties.class)
class CacheConfig {
  private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

  @Bean
  CacheManager cacheManager(
    RedisConnectionFactory connectionFactory, ExperimentCacheProperties properties
  ) {
    RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
      .entryTtl(properties.tenantTtl())
      .disableCachingNullValues();
    return RedisCacheManager.builder(connectionFactory).cacheDefaults(configuration).build();
  }

  @Bean
  CacheErrorHandler cacheErrorHandler() {
    return new CacheErrorHandler() {
      @Override
      public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache read failed for {}", cache.getName(), exception);
      }

      @Override
      public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache write failed for {}", cache.getName(), exception);
      }

      @Override
      public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache eviction failed for {}", cache.getName(), exception);
      }

      @Override
      public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache clear failed for {}", cache.getName(), exception);
      }
    };
  }
}
