package ai.visitorflow.demo.visitor.assignment.service;

import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.exception.ValidationException;
import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCache;
import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCacheDto;
import ai.visitorflow.demo.data.experiment.cache.VariantAllocationCacheDto;
import ai.visitorflow.demo.data.event.model.EventStatus;
import ai.visitorflow.demo.visitor.assignment.config.AssignmentProperties;
import ai.visitorflow.demo.visitor.assignment.dto.response.AssignResponse;
import ai.visitorflow.demo.visitor.assignment.mapper.AssignmentMapper;
import ai.visitorflow.demo.visitor.assignment.strategy.AssignmentEngine;
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
  private final List<AssignmentEngine> assignmentEngines;
  private final AssignmentMapper assignmentMapper;
  private final TrackingMapper trackingMapper;
  private final TrackingEventProducer trackingEventProducer;
  private final AssignmentProperties assignmentProperties;

  @Override
  public AssignResponse assign(List<Long> experimentIds) {
    validate(experimentIds);
    Long anonVisitorId = RequestContextHolder.anonVisitorId();
    Map<Long, VariantAllocationCacheDto> assignments = new LinkedHashMap<>();
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
      VariantAllocationCacheDto variant;
      try {
        AssignmentEngine engine = assignmentEngines.stream()
          .filter(candidate -> candidate.strategy() == config.getStrategy())
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Unsupported assignment strategy"));
        variant = engine.assign(anonVisitorId, config);
      } catch (RuntimeException exception) {
        variant = config.getVariants().stream()
          .filter(candidate -> candidate.getVariantId().equals(config.getDefaultVariantId()))
          .findFirst()
          .orElse(null);
        assignmentDegraded = true;
        degraded = true;
      }
      if (variant != null) {
        assignments.put(experimentId, variant);
        trackingEventProducer.sendBestEffort(trackingMapper.toEvent(
          experimentId, variant.getVariantId(), EventStatus.ASSIGNED, assignmentDegraded
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
