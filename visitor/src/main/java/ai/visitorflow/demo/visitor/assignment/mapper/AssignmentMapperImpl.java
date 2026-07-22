package ai.visitorflow.demo.visitor.assignment.mapper;

import ai.visitorflow.demo.data.experiment.cache.VariantAllocationCacheDto;
import ai.visitorflow.demo.visitor.assignment.dto.response.AssignResponse;
import ai.visitorflow.demo.visitor.assignment.dto.response.AssignmentVariantResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AssignmentMapperImpl implements AssignmentMapper {
  private final ModelMapper modelMapper;

  @Override
  public AssignResponse toResponse(
    Map<Long, VariantAllocationCacheDto> assignments, Long anonVisitorId, boolean degraded
  ) {
    Map<Long, AssignmentVariantResponse> responses = new LinkedHashMap<>();
    assignments.forEach((experimentId, variant) -> responses.put(
      experimentId, modelMapper.map(variant, AssignmentVariantResponse.class)
    ));
    AssignmentResult source = new AssignmentResult(responses, anonVisitorId, degraded);
    return modelMapper.map(source, AssignResponse.class);
  }

  private record AssignmentResult(
    Map<Long, AssignmentVariantResponse> assignments, Long anonVisitorId, boolean degraded
  ) {
  }
}
