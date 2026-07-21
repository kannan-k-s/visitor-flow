package ai.visitorflow.demo.visitor.tracking.consumer;

import ai.visitorflow.demo.data.event.repository.EventRepository;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class TrackingEventConsumerImpl implements TrackingEventConsumer {
  private final EventRepository eventRepository;

  @Override
  @Transactional
  public void consume(TrackingEventMqDto event) {
    eventRepository.insertIgnore(
      event.getExperimentId(), event.getVariantId(), event.getAnonVisitorId(), event.getStatus().value(),
      event.getOccurredAt(), event.isDegraded(), event.getTenantId()
    );
  }
}
