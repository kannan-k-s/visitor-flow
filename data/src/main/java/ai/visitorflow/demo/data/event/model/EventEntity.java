package ai.visitorflow.demo.data.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "events", uniqueConstraints = @UniqueConstraint(
  name = "uk_events_dedup", columnNames = {"tenant_id", "experiment_id", "anon_visitor_id", "status"}
))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "experiment_id", nullable = false)
  private Long experimentId;

  @Column(name = "variant_id", nullable = false)
  private Long variantId;

  @Column(name = "anon_visitor_id", nullable = false)
  private Long anonVisitorId;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  private boolean degraded;
}
