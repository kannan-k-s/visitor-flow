package ai.visitorflow.demo.visitor.tracking.consumer;

import static org.mockito.Mockito.verify;

import ai.visitorflow.demo.data.event.model.EventStatus;
import ai.visitorflow.demo.data.event.repository.EventRepository;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TrackingEventConsumerImplTest {
  @Test
  void writesBrokerEventDirectlyWithoutRedundantEntityMapping() {
    EventRepository repository = Mockito.mock(EventRepository.class);
    TrackingEventConsumer consumer = new TrackingEventConsumerImpl(repository);
    Instant occurredAt = Instant.parse("2026-07-22T00:00:00Z");
    TrackingEventMqDto event = TrackingEventMqDto.builder()
      .tenantId(11L)
      .experimentId(22L)
      .variantId(33L)
      .anonVisitorId(44L)
      .status(EventStatus.EXPOSED)
      .occurredAt(occurredAt)
      .degraded(false)
      .build();

    consumer.consume(event);

    verify(repository).insertIgnore(22L, 33L, 44L, "exposed", occurredAt, false, 11L);
  }
}
