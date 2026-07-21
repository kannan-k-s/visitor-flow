package ai.visitorflow.demo.data.experiment.repository;

import ai.visitorflow.demo.data.experiment.model.VariantEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariantRepository extends JpaRepository<VariantEntity, Long> {
  List<VariantEntity> findAllByExperimentIdAndTenantIdOrderByIdAsc(Long experimentId, Long tenantId);
  Optional<VariantEntity> findByIdAndExperimentIdAndTenantId(Long id, Long experimentId, Long tenantId);
  long deleteAllByExperimentIdAndTenantId(Long experimentId, Long tenantId);
  long deleteByIdAndExperimentIdAndTenantId(Long id, Long experimentId, Long tenantId);
}
