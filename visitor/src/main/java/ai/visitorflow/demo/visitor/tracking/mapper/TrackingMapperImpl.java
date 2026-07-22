package ai.visitorflow.demo.visitor.tracking.mapper;

import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.event.model.EventStatus;
import ai.visitorflow.demo.visitor.tracking.dto.response.TrackResponse;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TrackingMapperImpl implements TrackingMapper {
  private final ModelMapper modelMapper;

  @Override
  public TrackingEventMqDto toEvent(
    Long experimentId, Long variantId, EventStatus status, boolean degraded
  ) {
    TrackingEventSource source = new TrackingEventSource(
      RequestContextHolder.tenantId(), experimentId, variantId, RequestContextHolder.anonVisitorId(),
      status, Instant.now(), degraded
    );
    return modelMapper.map(source, TrackingEventMqDto.class);
  }

  @Override
  public TrackResponse toResponse(int accepted) {
    return modelMapper.map(new TrackResult(accepted), TrackResponse.class);
  }

  private record TrackingEventSource(
    Long tenantId, Long experimentId, Long variantId, Long anonVisitorId,
    EventStatus status, Instant occurredAt, boolean degraded
  ) {
  }

  private record TrackResult(int accepted) {
  }
}
