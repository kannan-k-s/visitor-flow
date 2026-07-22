package ai.visitorflow.demo.data.tenant;

import ai.visitorflow.demo.data.exception.NotFoundException;
import ai.visitorflow.demo.data.tenant.model.TenantEntity;
import ai.visitorflow.demo.data.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TenantResolverImpl implements TenantResolver {
  private final TenantRepository tenantRepository;

  @Override
  @Cacheable(cacheNames = "tenant", key = "#tenantName")
  public Long resolve(String tenantName) {
    return tenantRepository.findByName(tenantName)
      .map(TenantEntity::getId)
      .orElseThrow(() -> new NotFoundException("tenant", tenantName));
  }
}
