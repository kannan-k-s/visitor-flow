package ai.visitorflow.demo.visitor.tracking.mapper;

import ai.visitorflow.demo.data.event.model.EventEntity;
import ai.visitorflow.demo.visitor.tracking.dto.response.TrackResponse;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;

public interface TrackingMapper {
  TrackingEventMqDto toEvent(Long experimentId, Long variantId, String status, boolean degraded);
  EventEntity toEntity(TrackingEventMqDto event);
  TrackResponse toResponse(int accepted);
}
