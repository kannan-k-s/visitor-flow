package ai.visitorflow.demo.admin.experiment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExperimentResponse {
  @JsonProperty("id")
  private Long id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("strategy")
  private String strategy;

  @JsonProperty("variants")
  private List<VariantResponse> variants;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonProperty("analytics_warning")
  private String analyticsWarning;
}
