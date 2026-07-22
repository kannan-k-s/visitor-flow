package ai.visitorflow.demo.visitor.tracking.producer;

import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import java.util.List;

public interface TrackingEventProducer {
  void sendBestEffort(TrackingEventMqDto event);
  void sendDurably(List<TrackingEventMqDto> events);
}
