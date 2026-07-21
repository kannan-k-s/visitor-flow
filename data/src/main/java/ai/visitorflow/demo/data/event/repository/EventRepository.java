package ai.visitorflow.demo.data.event.repository;

import ai.visitorflow.demo.data.event.model.EventEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<EventEntity, Long> {
  @Modifying
  @Query(value = """
    INSERT IGNORE INTO events
      (tenant_id, experiment_id, variant_id, anon_visitor_id, status, occurred_at, degraded)
    VALUES
      (:tenantId, :experimentId, :variantId, :anonVisitorId, :status, :occurredAt, :degraded)
    """, nativeQuery = true)
  int insertIgnore(
    @Param("experimentId") Long experimentId, @Param("variantId") Long variantId,
    @Param("anonVisitorId") Long anonVisitorId, @Param("status") String status,
    @Param("occurredAt") Instant occurredAt, @Param("degraded") boolean degraded,
    @Param("tenantId") Long tenantId
  );

  @Query(value = """
    SELECT e.variant_id AS variantId, e.status AS status, COUNT(*) AS total
    FROM events e
    WHERE e.tenant_id = :tenantId
      AND e.experiment_id = :experimentId
      AND (
        (e.status = 'assigned' AND e.degraded = FALSE)
        OR e.status = 'exposed'
        OR (e.status = 'converted' AND EXISTS (
          SELECT 1 FROM events exposure
          WHERE exposure.tenant_id = e.tenant_id
            AND exposure.experiment_id = e.experiment_id
            AND exposure.variant_id = e.variant_id
            AND exposure.anon_visitor_id = e.anon_visitor_id
            AND exposure.status = 'exposed'
        ))
      )
    GROUP BY e.variant_id, e.status
    """, nativeQuery = true)
  List<VariantStatusCount> findVariantStatusCounts(
    @Param("experimentId") Long experimentId, @Param("tenantId") Long tenantId
  );

  @Query(value = """
    SELECT COUNT(*)
    FROM events conversion_event
    WHERE conversion_event.tenant_id = :tenantId
      AND conversion_event.experiment_id = :experimentId
      AND conversion_event.status = 'converted'
      AND NOT EXISTS (
        SELECT 1 FROM events exposure
        WHERE exposure.tenant_id = conversion_event.tenant_id
          AND exposure.experiment_id = conversion_event.experiment_id
          AND exposure.variant_id = conversion_event.variant_id
          AND exposure.anon_visitor_id = conversion_event.anon_visitor_id
          AND exposure.status = 'exposed'
      )
    """, nativeQuery = true)
  long countOrphanConversions(
    @Param("experimentId") Long experimentId, @Param("tenantId") Long tenantId
  );
}
