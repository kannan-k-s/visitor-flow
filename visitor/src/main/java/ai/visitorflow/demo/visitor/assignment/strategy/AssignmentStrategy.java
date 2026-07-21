package ai.visitorflow.demo.visitor.assignment.strategy;

import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCacheDto;

public interface AssignmentStrategy {
  String name();
  Long assign(long anonVisitorId, ExperimentConfigCacheDto experiment);
}
