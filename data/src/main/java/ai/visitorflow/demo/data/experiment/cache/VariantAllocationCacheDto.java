package ai.visitorflow.demo.data.experiment.cache;

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
public class VariantAllocationCacheDto {
  @JsonProperty("variant_id")
  private Long variantId;

  @JsonProperty("allocation_percentage")
  private BigDecimal allocationPercentage;

  @JsonProperty("lower_bucket")
  private int lowerBucket;

  @JsonProperty("upper_bucket")
  private int upperBucket;
}
