package ai.visitorflow.demo.visitor.tracking.consumer;

import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;

public interface TrackingEventConsumer {
  void consume(TrackingEventMqDto event);
}
