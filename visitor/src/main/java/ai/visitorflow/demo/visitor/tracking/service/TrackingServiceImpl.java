package ai.visitorflow.demo.visitor.tracking.service;

import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.exception.ValidationException;
import ai.visitorflow.demo.visitor.tracking.config.TrackingProperties;
import ai.visitorflow.demo.visitor.tracking.dto.request.TrackRequest;
import ai.visitorflow.demo.visitor.tracking.dto.response.TrackResponse;
import ai.visitorflow.demo.visitor.tracking.mapper.TrackingMapper;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import ai.visitorflow.demo.visitor.tracking.producer.TrackingEventProducer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TrackingServiceImpl implements TrackingService {
  private final TrackingMapper trackingMapper;
  private final TrackingEventProducer trackingEventProducer;
  private final TrackingProperties trackingProperties;

  @Override
  public TrackResponse track(TrackRequest request) {
    if (!request.getStatus().isClientReportable()) {
      throw new ValidationException("Only exposed and converted tracking statuses are accepted");
    }
    if (request.getData().size() > trackingProperties.maxEntries()) {
      throw new ValidationException("At most " + trackingProperties.maxEntries() + " tracking entries are allowed");
    }
    if (RequestContextHolder.anonVisitorId() == null) {
      return trackingMapper.toResponse(0);
    }
    List<TrackingEventMqDto> events = request.getData().entrySet().stream()
      .map(entry -> trackingMapper.toEvent(entry.getKey(), entry.getValue(), request.getStatus(), false))
      .toList();
    trackingEventProducer.sendDurably(events);
    return trackingMapper.toResponse(events.size());
  }
}
