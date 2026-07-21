package ai.visitorflow.demo.visitor.assignment.service;

import ai.visitorflow.demo.visitor.assignment.dto.response.AssignResponse;
import java.util.List;

public interface AssignmentService {
  AssignResponse assign(List<Long> experimentIds);
}
