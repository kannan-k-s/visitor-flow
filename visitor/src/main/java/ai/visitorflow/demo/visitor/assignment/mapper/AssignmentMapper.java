package ai.visitorflow.demo.visitor.assignment.mapper;

import ai.visitorflow.demo.visitor.assignment.dto.response.AssignResponse;
import java.util.Map;

public interface AssignmentMapper {
  AssignResponse toResponse(Map<Long, Long> assignments, Long anonVisitorId, boolean degraded);
}
