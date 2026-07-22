package ai.visitorflow.demo.admin.results.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ExperimentResultsResponse {
  @JsonProperty("experiment_id")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long experimentId;

  @JsonProperty("variants")
  private List<VariantResultResponse> variants;

  @JsonProperty("orphan_converted")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long orphanConverted;
}
