package ai.visitorflow.demo.admin.experiment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DeleteExperimentResponse {
  @JsonProperty("experiment_id")
  private Long experimentId;

  @JsonProperty("deleted")
  private boolean deleted;
}
