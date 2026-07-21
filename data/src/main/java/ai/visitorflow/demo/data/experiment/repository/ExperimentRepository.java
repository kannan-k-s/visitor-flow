package ai.visitorflow.demo.data.experiment.repository;

import ai.visitorflow.demo.data.experiment.model.ExperimentEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRepository extends JpaRepository<ExperimentEntity, Long> {
  Optional<ExperimentEntity> findByIdAndTenantId(Long id, Long tenantId);
  Page<ExperimentEntity> findAllByTenantId(Pageable pageable, Long tenantId);
  boolean existsByNameAndTenantId(String name, Long tenantId);
  boolean existsByNameAndIdNotAndTenantId(String name, Long id, Long tenantId);
  long deleteByIdAndTenantId(Long id, Long tenantId);
}
