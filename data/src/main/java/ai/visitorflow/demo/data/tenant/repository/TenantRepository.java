package ai.visitorflow.demo.data.tenant.repository;

import ai.visitorflow.demo.data.tenant.model.TenantEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {
  Optional<TenantEntity> findByName(String name);
}
