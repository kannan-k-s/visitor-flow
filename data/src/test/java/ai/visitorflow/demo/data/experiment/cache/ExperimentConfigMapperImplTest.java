package ai.visitorflow.demo.data.experiment.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import ai.visitorflow.demo.data.experiment.model.ExperimentEntity;
import ai.visitorflow.demo.data.experiment.model.AssignmentStrategy;
import ai.visitorflow.demo.data.experiment.model.VariantEntity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration.AccessLevel;

class ExperimentConfigMapperImplTest {
  private ExperimentConfigMapperImpl mapper;

  @BeforeEach
  void setUp() {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.getConfiguration().setFieldMatchingEnabled(true).setFieldAccessLevel(AccessLevel.PRIVATE);
    mapper = new ExperimentConfigMapperImpl(modelMapper);
    mapper.afterPropertiesSet();
  }

  @Test
  void buildsStableBasisPointRangesAndDefaultVariant() {
    ExperimentEntity experiment = ExperimentEntity.builder()
      .id(7L).tenantId(1L).strategy(AssignmentStrategy.HASH).build();
    List<VariantEntity> variants = List.of(
      variant(11L, true, "50.00"), variant(12L, false, "35.25"), variant(13L, false, "14.75")
    );

    ExperimentConfigCacheDto result = mapper.toCache(experiment, variants);

    assertThat(result.getDefaultVariantId()).isEqualTo(11L);
    assertThat(result.getVariants()).extracting(VariantAllocationCacheDto::getContent)
      .containsExactly("content", "content", "content");
    assertThat(result.getVariants())
      .extracting(VariantAllocationCacheDto::getLowerBucket, VariantAllocationCacheDto::getUpperBucket)
      .containsExactly(tuple(0, 4999), tuple(5000, 8524), tuple(8525, 9999));
  }

  private VariantEntity variant(Long id, boolean defaultVariant, String allocation) {
    return VariantEntity.builder()
      .id(id)
      .tenantId(1L)
      .experimentId(7L)
      .defaultVariant(defaultVariant)
      .content("content")
      .allocationPercentage(new BigDecimal(allocation))
      .build();
  }
}
