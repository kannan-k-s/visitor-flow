package ai.visitorflow.demo.visitor.tracking.mq;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TrackingEventMqDto {
  @JsonProperty("tenant_id")
  private Long tenantId;

  @JsonProperty("experiment_id")
  private Long experimentId;

  @JsonProperty("variant_id")
  private Long variantId;

  @JsonProperty("anon_visitor_id")
  private Long anonVisitorId;

  @JsonProperty("status")
  private String status;

  @JsonProperty("occurred_at")
  private Instant occurredAt;

  @JsonProperty("degraded")
  private boolean degraded;
}
