package ai.visitorflow.demo.data.experiment.cache;

import ai.visitorflow.demo.data.experiment.model.ExperimentEntity;
import ai.visitorflow.demo.data.experiment.model.VariantEntity;
import java.util.List;

public interface ExperimentConfigMapper {
  ExperimentConfigCacheDto toCache(ExperimentEntity experiment, List<VariantEntity> variants);
}
