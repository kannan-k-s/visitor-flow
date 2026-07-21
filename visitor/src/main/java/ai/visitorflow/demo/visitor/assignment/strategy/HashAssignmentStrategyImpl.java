package ai.visitorflow.demo.visitor.assignment.strategy;

import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCacheDto;
import ai.visitorflow.demo.data.experiment.cache.VariantAllocationCacheDto;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
class HashAssignmentStrategyImpl implements AssignmentStrategy {
  private static final int BUCKET_COUNT = 10_000;

  @Override
  public String name() {
    return "hash";
  }

  @Override
  public Long assign(long anonVisitorId, ExperimentConfigCacheDto experiment) {
    String input = anonVisitorId + ":" + experiment.getExperimentId();
    int hash = Hashing.murmur3_32_fixed().hashString(input, StandardCharsets.UTF_8).asInt();
    int bucket = Math.floorMod(hash, BUCKET_COUNT);
    return experiment.getVariants().stream()
      .filter(variant -> contains(variant, bucket))
      .map(VariantAllocationCacheDto::getVariantId)
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No allocation range contains bucket " + bucket));
  }

  private boolean contains(VariantAllocationCacheDto variant, int bucket) {
    return bucket >= variant.getLowerBucket() && bucket <= variant.getUpperBucket();
  }
}
