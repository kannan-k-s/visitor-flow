package ai.visitorflow.demo.visitor.tracking.mapper;

import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.event.model.EventEntity;
import ai.visitorflow.demo.visitor.tracking.dto.response.TrackResponse;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TrackingMapperImpl implements TrackingMapper {
  private final ModelMapper modelMapper;

  @Override
  public TrackingEventMqDto toEvent(
    Long experimentId, Long variantId, String status, boolean degraded
  ) {
    TrackingEventSource source = TrackingEventSource.builder()
      .tenantId(RequestContextHolder.tenantId())
      .experimentId(experimentId)
      .variantId(variantId)
      .anonVisitorId(RequestContextHolder.anonVisitorId())
      .status(status)
      .occurredAt(Instant.now())
      .degraded(degraded)
      .build();
    return modelMapper.map(source, TrackingEventMqDto.class);
  }

  @Override
  public EventEntity toEntity(TrackingEventMqDto event) {
    return modelMapper.map(event, EventEntity.class);
  }

  @Override
  public TrackResponse toResponse(int accepted) {
    return modelMapper.map(TrackResult.builder().accepted(accepted).build(), TrackResponse.class);
  }

  @Getter
  @Builder
  private static class TrackingEventSource {
    private Long tenantId;
    private Long experimentId;
    private Long variantId;
    private Long anonVisitorId;
    private String status;
    private Instant occurredAt;
    private boolean degraded;
  }

  @Getter
  @Builder
  private static class TrackResult {
    private int accepted;
  }
}
