package ai.visitorflow.demo.data.tenant.repository;

import ai.visitorflow.demo.data.tenant.model.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
  Optional<UserEntity> findByEmailAndTenantId(String email, Long tenantId);
  Optional<UserEntity> findByIdAndTenantId(Long id, Long tenantId);
}
