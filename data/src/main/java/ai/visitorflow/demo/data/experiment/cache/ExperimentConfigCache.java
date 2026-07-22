package ai.visitorflow.demo.data.experiment.cache;

import java.util.List;
import java.util.Map;

public interface ExperimentConfigCache {
  Map<Long, ExperimentConfigCacheDto> get(List<Long> experimentIds);
  void clearAfterCommit();
}
