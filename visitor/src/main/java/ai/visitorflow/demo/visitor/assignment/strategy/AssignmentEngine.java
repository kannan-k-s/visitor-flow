package ai.visitorflow.demo.visitor.assignment.strategy;

import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCacheDto;
import ai.visitorflow.demo.data.experiment.cache.VariantAllocationCacheDto;
import ai.visitorflow.demo.data.experiment.model.AssignmentStrategy;

public interface AssignmentEngine {
  AssignmentStrategy strategy();
  VariantAllocationCacheDto assign(long anonVisitorId, ExperimentConfigCacheDto experiment);
}
