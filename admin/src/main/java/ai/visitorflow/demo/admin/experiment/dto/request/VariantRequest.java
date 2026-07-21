package ai.visitorflow.demo.admin.experiment.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class VariantRequest {
  @JsonProperty("id")
  @Positive
  private Long id;

  @JsonProperty("content")
  @NotBlank
  private String content;

  @JsonProperty("alloc_pct")
  @NotNull
  @DecimalMin(value = "0.01")
  @Digits(integer = 3, fraction = 2)
  private BigDecimal allocationPercentage;

  @JsonProperty("is_default")
  private boolean defaultVariant;
}
