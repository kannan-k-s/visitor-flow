package ai.visitorflow.demo.data.experiment.cache;

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
public class ExperimentConfigCacheDto {
  @JsonProperty("experiment_id")
  private Long experimentId;

  @JsonProperty("strategy")
  private String strategy;

  @JsonProperty("default_variant_id")
  private Long defaultVariantId;

  @JsonProperty("variants")
  private List<VariantAllocationCacheDto> variants;
}
