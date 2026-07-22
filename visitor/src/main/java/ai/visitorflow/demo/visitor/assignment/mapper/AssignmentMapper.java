package ai.visitorflow.demo.visitor.assignment.mapper;

import ai.visitorflow.demo.data.experiment.cache.VariantAllocationCacheDto;
import ai.visitorflow.demo.visitor.assignment.dto.response.AssignResponse;
import java.util.Map;

public interface AssignmentMapper {
  AssignResponse toResponse(
    Map<Long, VariantAllocationCacheDto> assignments, Long anonVisitorId, boolean degraded
  );
}
