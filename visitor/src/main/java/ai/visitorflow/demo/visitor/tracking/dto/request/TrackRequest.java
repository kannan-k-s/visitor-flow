package ai.visitorflow.demo.visitor.tracking.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class TrackRequest {
  @JsonProperty("status")
  @NotNull
  @Pattern(regexp = "exposed|converted")
  private String status;

  @JsonProperty("data")
  @NotEmpty
  @Size(max = 20)
  private Map<@Positive Long, @Positive Long> data;
}
