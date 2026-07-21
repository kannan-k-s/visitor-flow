package ai.visitorflow.demo.admin.experiment.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VariantResponse {
  @JsonProperty("id")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long id;

  @JsonProperty("content")
  private String content;

  @JsonProperty("alloc_pct")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private BigDecimal allocationPercentage;

  @JsonProperty("is_default")
  private boolean defaultVariant;
}
