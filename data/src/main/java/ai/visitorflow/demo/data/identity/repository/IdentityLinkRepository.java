package ai.visitorflow.demo.data.identity.repository;

import ai.visitorflow.demo.data.identity.model.IdentityLinkEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityLinkRepository extends JpaRepository<IdentityLinkEntity, Long> {
  Optional<IdentityLinkEntity> findByVisitorIdAndTenantId(String visitorId, Long tenantId);

  @Modifying
  @Query(value = """
    INSERT IGNORE INTO identity_links (tenant_id, visitor_id, anon_visitor_id, created_at)
    VALUES (:tenantId, :visitorId, :anonVisitorId, :createdAt)
    """, nativeQuery = true)
  int insertIgnore(
    @Param("visitorId") String visitorId, @Param("anonVisitorId") Long anonVisitorId,
    @Param("createdAt") Instant createdAt, @Param("tenantId") Long tenantId
  );
}
