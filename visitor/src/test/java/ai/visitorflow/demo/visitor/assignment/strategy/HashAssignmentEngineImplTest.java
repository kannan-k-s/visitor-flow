package ai.visitorflow.demo.visitor.assignment.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCacheDto;
import ai.visitorflow.demo.data.experiment.cache.VariantAllocationCacheDto;
import ai.visitorflow.demo.data.experiment.model.AssignmentStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class HashAssignmentEngineImplTest {
  private final HashAssignmentEngineImpl engine = new HashAssignmentEngineImpl();

  @Test
  void returnsSameVariantForSameAnonAndExperiment() {
    ExperimentConfigCacheDto experiment = experiment(50, 50);

    VariantAllocationCacheDto first = engine.assign(918273L, experiment);

    assertThat(engine.assign(918273L, experiment).getVariantId()).isEqualTo(first.getVariantId());
    assertThat(engine.assign(918273L, experiment).getContent()).isEqualTo(first.getContent());
  }

  @Test
  void approximatesConfiguredAllocationAcrossPopulation() {
    ExperimentConfigCacheDto experiment = experiment(90, 10);
    int secondVariant = 0;
    int population = 100_000;

    for (long anonId = 1; anonId <= population; anonId++) {
      if (engine.assign(anonId, experiment).getVariantId().equals(102L)) {
        secondVariant++;
      }
    }

    double percentage = secondVariant * 100.0 / population;
    assertThat(percentage).isBetween(9.5, 10.5);
  }

  private ExperimentConfigCacheDto experiment(int firstPercentage, int secondPercentage) {
    int boundary = firstPercentage * 100;
    return ExperimentConfigCacheDto.builder()
      .experimentId(77L)
      .strategy(AssignmentStrategy.HASH)
      .defaultVariantId(101L)
      .variants(List.of(
        allocation(101L, firstPercentage, 0, boundary - 1),
        allocation(102L, secondPercentage, boundary, 9999)
      ))
      .build();
  }

  private VariantAllocationCacheDto allocation(Long id, int percentage, int lower, int upper) {
    return VariantAllocationCacheDto.builder()
      .variantId(id)
      .content("variant-" + id)
      .allocationPercentage(BigDecimal.valueOf(percentage))
      .lowerBucket(lower)
      .upperBucket(upper)
      .build();
  }
}
