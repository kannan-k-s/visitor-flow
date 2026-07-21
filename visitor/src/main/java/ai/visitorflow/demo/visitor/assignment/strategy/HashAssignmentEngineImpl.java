package ai.visitorflow.demo.visitor.assignment.strategy;

import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCacheDto;
import ai.visitorflow.demo.data.experiment.cache.VariantAllocationCacheDto;
import ai.visitorflow.demo.data.experiment.model.AssignmentStrategy;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
class HashAssignmentEngineImpl implements AssignmentEngine {
  private static final int BUCKET_COUNT = 10_000;

  @Override
  public AssignmentStrategy strategy() {
    return AssignmentStrategy.HASH;
  }

  @Override
  public VariantAllocationCacheDto assign(long anonVisitorId, ExperimentConfigCacheDto experiment) {
    String input = anonVisitorId + ":" + experiment.getExperimentId();
    int hash = Hashing.murmur3_32_fixed().hashString(input, StandardCharsets.UTF_8).asInt();
    int bucket = Math.floorMod(hash, BUCKET_COUNT);
    return experiment.getVariants().stream()
      .filter(variant -> contains(variant, bucket))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No allocation range contains bucket " + bucket));
  }

  private boolean contains(VariantAllocationCacheDto variant, int bucket) {
    return bucket >= variant.getLowerBucket() && bucket <= variant.getUpperBucket();
  }
}
