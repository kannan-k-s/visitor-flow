package ai.visitorflow.demo.visitor.tracking.mapper;

import ai.visitorflow.demo.data.event.model.EventStatus;
import ai.visitorflow.demo.visitor.tracking.dto.response.TrackResponse;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;

public interface TrackingMapper {
  TrackingEventMqDto toEvent(Long experimentId, Long variantId, EventStatus status, boolean degraded);
  TrackResponse toResponse(int accepted);
}
