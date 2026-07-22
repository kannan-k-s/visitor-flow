package ai.visitorflow.demo.admin.results.dto.response;

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
public class VariantResultResponse {
  @JsonProperty("variant_id")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long variantId;

  @JsonProperty("assigned")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long assigned;

  @JsonProperty("exposed")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long exposed;

  @JsonProperty("converted")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long converted;

  @JsonProperty("conversion_rate")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private BigDecimal conversionRate;
}
