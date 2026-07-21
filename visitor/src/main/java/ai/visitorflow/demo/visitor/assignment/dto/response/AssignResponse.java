package ai.visitorflow.demo.visitor.assignment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AssignResponse {
  @JsonProperty("assignments")
  private Map<Long, Long> assignments;

  @JsonProperty("anon_visitor_id")
  private Long anonVisitorId;

  @JsonProperty("degraded")
  private boolean degraded;
}
