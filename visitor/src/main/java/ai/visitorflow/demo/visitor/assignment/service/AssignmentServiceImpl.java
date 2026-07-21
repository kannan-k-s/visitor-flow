package ai.visitorflow.demo.visitor.assignment.service;

import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.exception.ValidationException;
import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCache;
import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCacheDto;
import ai.visitorflow.demo.visitor.assignment.config.AssignmentProperties;
import ai.visitorflow.demo.visitor.assignment.dto.response.AssignResponse;
import ai.visitorflow.demo.visitor.assignment.mapper.AssignmentMapper;
import ai.visitorflow.demo.visitor.assignment.strategy.AssignmentStrategy;
import ai.visitorflow.demo.visitor.tracking.mapper.TrackingMapper;
import ai.visitorflow.demo.visitor.tracking.producer.TrackingEventProducer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AssignmentServiceImpl implements AssignmentService {
  private final ExperimentConfigCache experimentConfigCache;
  private final List<AssignmentStrategy> strategies;
  private final AssignmentMapper assignmentMapper;
  private final TrackingMapper trackingMapper;
  private final TrackingEventProducer trackingEventProducer;
  private final AssignmentProperties assignmentProperties;

  @Override
  public AssignResponse assign(List<Long> experimentIds) {
    validate(experimentIds);
    Long anonVisitorId = RequestContextHolder.anonVisitorId();
    Map<Long, Long> assignments = new LinkedHashMap<>();
    boolean degraded = false;
    Map<Long, ExperimentConfigCacheDto> configs;
    try {
      configs = experimentConfigCache.get(experimentIds);
    } catch (RuntimeException exception) {
      return assignmentMapper.toResponse(assignments, anonVisitorId, true);
    }
    for (Long experimentId : experimentIds.stream().distinct().toList()) {
      ExperimentConfigCacheDto config = configs.get(experimentId);
      if (config == null) {
        degraded = true;
        continue;
      }
      boolean assignmentDegraded = false;
      Long variantId;
      try {
        AssignmentStrategy strategy = strategies.stream()
          .filter(candidate -> candidate.name().equals(config.getStrategy()))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Unsupported assignment strategy"));
        variantId = strategy.assign(anonVisitorId, config);
      } catch (RuntimeException exception) {
        variantId = config.getDefaultVariantId();
        assignmentDegraded = true;
        degraded = true;
      }
      if (variantId != null) {
        assignments.put(experimentId, variantId);
        trackingEventProducer.sendBestEffort(trackingMapper.toEvent(
          experimentId, variantId, "assigned", assignmentDegraded
        ));
      }
    }
    return assignmentMapper.toResponse(assignments, anonVisitorId, degraded);
  }

  private void validate(List<Long> experimentIds) {
    if (experimentIds == null || experimentIds.isEmpty()) {
      throw new ValidationException("At least one experiment id is required");
    }
    if (experimentIds.size() > assignmentProperties.maxExperiments()) {
      throw new ValidationException("At most " + assignmentProperties.maxExperiments() + " experiments are allowed");
    }
    if (experimentIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new ValidationException("Experiment ids must be positive numbers");
    }
  }
}
