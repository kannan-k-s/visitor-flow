package ai.visitorflow.demo.visitor.assignment.mapper;

import ai.visitorflow.demo.visitor.assignment.dto.response.AssignResponse;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AssignmentMapperImpl implements AssignmentMapper {
  private final ModelMapper modelMapper;

  @Override
  public AssignResponse toResponse(Map<Long, Long> assignments, Long anonVisitorId, boolean degraded) {
    AssignmentResult source = AssignmentResult.builder()
      .assignments(assignments)
      .anonVisitorId(anonVisitorId)
      .degraded(degraded)
      .build();
    return modelMapper.map(source, AssignResponse.class);
  }

  @Getter
  @Builder
  private static class AssignmentResult {
    private Map<Long, Long> assignments;
    private Long anonVisitorId;
    private boolean degraded;
  }
}
