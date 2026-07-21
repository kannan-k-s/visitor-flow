package ai.visitorflow.demo.admin.experiment.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class UpdateExperimentRequest {
  @JsonProperty("name")
  @NotBlank
  @Size(max = 160)
  private String name;

  @JsonProperty("strategy")
  @Builder.Default
  private String strategy = "hash";

  @JsonProperty("variants")
  @Valid
  @NotEmpty
  @Size(min = 2)
  private List<VariantRequest> variants;
}
