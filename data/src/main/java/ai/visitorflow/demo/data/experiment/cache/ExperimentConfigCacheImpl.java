package ai.visitorflow.demo.data.experiment.cache;

import ai.visitorflow.demo.data.config.ExperimentCacheProperties;
import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.experiment.model.ExperimentEntity;
import ai.visitorflow.demo.data.experiment.repository.ExperimentRepository;
import ai.visitorflow.demo.data.experiment.repository.VariantRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class ExperimentConfigCacheImpl implements ExperimentConfigCache {
  private static final Logger log = LoggerFactory.getLogger(ExperimentConfigCacheImpl.class);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final ExperimentRepository experimentRepository;
  private final VariantRepository variantRepository;
  private final ExperimentConfigMapper experimentConfigMapper;
  private final ExperimentCacheProperties cacheProperties;

  @Override
  public Map<Long, ExperimentConfigCacheDto> get(List<Long> experimentIds) {
    Long tenantId = RequestContextHolder.tenantId();
    List<Long> uniqueIds = experimentIds.stream().distinct().toList();
    Map<Long, ExperimentConfigCacheDto> resolved = readRedis(uniqueIds, tenantId);
    List<Long> missing = uniqueIds.stream().filter(id -> !resolved.containsKey(id)).toList();
    if (missing.isEmpty()) {
      return resolved;
    }
    Map<Long, ExperimentConfigCacheDto> loaded = loadDatabase(missing, tenantId);
    resolved.putAll(loaded);
    writeRedis(loaded, tenantId);
    return resolved;
  }

  @Override
  public void clear() {
    Long tenantId = RequestContextHolder.tenantId();
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          clearNow(tenantId);
        }
      });
      return;
    }
    clearNow(tenantId);
  }

  private Map<Long, ExperimentConfigCacheDto> readRedis(List<Long> experimentIds, Long tenantId) {
    Map<Long, ExperimentConfigCacheDto> result = new LinkedHashMap<>();
    try {
      HashOperations<String, String, String> hashes = redisTemplate.opsForHash();
      List<String> values = hashes.multiGet(key(tenantId), experimentIds.stream().map(String::valueOf).toList());
      for (int index = 0; index < experimentIds.size(); index++) {
        String value = values.get(index);
        if (value != null) {
          result.put(experimentIds.get(index), objectMapper.readValue(value, ExperimentConfigCacheDto.class));
        }
      }
    } catch (RuntimeException exception) {
      log.warn("Experiment cache read failed for tenant {}", tenantId, exception);
    }
    return result;
  }

  private Map<Long, ExperimentConfigCacheDto> loadDatabase(List<Long> experimentIds, Long tenantId) {
    Map<Long, ExperimentConfigCacheDto> result = new LinkedHashMap<>();
    for (Long experimentId : experimentIds) {
      ExperimentEntity experiment = experimentRepository.findByIdAndTenantId(experimentId, tenantId).orElse(null);
      if (experiment != null) {
        result.put(experimentId, experimentConfigMapper.toCache(experiment,
          variantRepository.findAllByExperimentIdAndTenantIdOrderByIdAsc(experimentId, tenantId)));
      }
    }
    return result;
  }

  private void writeRedis(Map<Long, ExperimentConfigCacheDto> configs, Long tenantId) {
    if (configs.isEmpty()) {
      return;
    }
    try {
      Map<String, String> serialized = new LinkedHashMap<>();
      for (Map.Entry<Long, ExperimentConfigCacheDto> entry : configs.entrySet()) {
        serialized.put(String.valueOf(entry.getKey()), objectMapper.writeValueAsString(entry.getValue()));
      }
      redisTemplate.opsForHash().putAll(key(tenantId), serialized);
      redisTemplate.expire(key(tenantId), cacheProperties.experimentTtl());
    } catch (RuntimeException exception) {
      log.warn("Experiment cache write failed for tenant {}", tenantId, exception);
    }
  }

  private void clearNow(Long tenantId) {
    try {
      redisTemplate.delete(key(tenantId));
    } catch (RuntimeException exception) {
      log.warn("Experiment cache invalidation failed for tenant {}", tenantId, exception);
    }
  }

  private String key(Long tenantId) {
    return "cfg:" + tenantId;
  }
}
