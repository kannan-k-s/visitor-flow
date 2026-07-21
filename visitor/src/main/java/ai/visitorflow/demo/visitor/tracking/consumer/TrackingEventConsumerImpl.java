package ai.visitorflow.demo.visitor.tracking.consumer;

import ai.visitorflow.demo.data.event.model.EventEntity;
import ai.visitorflow.demo.data.event.repository.EventRepository;
import ai.visitorflow.demo.visitor.tracking.mapper.TrackingMapper;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class TrackingEventConsumerImpl implements TrackingEventConsumer {
  private final TrackingMapper trackingMapper;
  private final EventRepository eventRepository;

  @Override
  @Transactional
  public void consume(TrackingEventMqDto event) {
    EventEntity entity = trackingMapper.toEntity(event);
    eventRepository.insertIgnore(
      entity.getExperimentId(), entity.getVariantId(), entity.getAnonVisitorId(), entity.getStatus(),
      entity.getOccurredAt(), entity.isDegraded(), entity.getTenantId()
    );
  }
}
