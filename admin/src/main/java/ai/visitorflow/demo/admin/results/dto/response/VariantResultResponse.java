package ai.visitorflow.demo.admin.results.dto.response;

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
  private Long variantId;

  @JsonProperty("assigned")
  private long assigned;

  @JsonProperty("exposed")
  private long exposed;

  @JsonProperty("converted")
  private long converted;

  @JsonProperty("conversion_rate")
  private BigDecimal conversionRate;
}
