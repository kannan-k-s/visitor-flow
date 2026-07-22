package ai.visitorflow.demo.admin.experiment.dto.response;

import ai.visitorflow.demo.data.experiment.model.AssignmentStrategy;
import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ExperimentSummaryResponse {
  @JsonProperty("id")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("strategy")
  private AssignmentStrategy strategy;
}
