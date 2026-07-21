package ai.visitorflow.demo.data.experiment.cache;

import ai.visitorflow.demo.data.experiment.model.ExperimentEntity;
import ai.visitorflow.demo.data.experiment.model.VariantEntity;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ExperimentConfigMapperImpl implements ExperimentConfigMapper, InitializingBean {
  private final ModelMapper modelMapper;

  @Override
  public void afterPropertiesSet() {
    Converter<ExperimentConfigSource, ExperimentConfigCacheDto> converter = context -> {
      ExperimentConfigSource source = context.getSource();
      int lowerBucket = 0;
      Long defaultVariantId = null;
      List<VariantAllocationCacheDto> allocations = new ArrayList<>();
      for (VariantEntity variant : source.variants()) {
        int bucketCount = variant.getAllocationPercentage().movePointRight(2)
          .setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        int upperBucket = lowerBucket + bucketCount - 1;
        allocations.add(VariantAllocationCacheDto.builder()
          .variantId(variant.getId())
          .allocationPercentage(variant.getAllocationPercentage())
          .lowerBucket(lowerBucket)
          .upperBucket(upperBucket)
          .build());
        if (variant.isDefaultVariant()) {
          defaultVariantId = variant.getId();
        }
        lowerBucket = upperBucket + 1;
      }
      return ExperimentConfigCacheDto.builder()
        .experimentId(source.experiment().getId())
        .strategy(source.experiment().getStrategy())
        .defaultVariantId(defaultVariantId)
        .variants(List.copyOf(allocations))
        .build();
    };
    modelMapper.createTypeMap(ExperimentConfigSource.class, ExperimentConfigCacheDto.class)
      .setConverter(converter);
  }

  @Override
  public ExperimentConfigCacheDto toCache(ExperimentEntity experiment, List<VariantEntity> variants) {
    return modelMapper.map(new ExperimentConfigSource(experiment, variants), ExperimentConfigCacheDto.class);
  }

  private record ExperimentConfigSource(ExperimentEntity experiment, List<VariantEntity> variants) {
  }
}
