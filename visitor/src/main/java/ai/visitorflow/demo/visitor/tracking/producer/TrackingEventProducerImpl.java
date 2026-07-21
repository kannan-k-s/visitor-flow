package ai.visitorflow.demo.visitor.tracking.producer;

import ai.visitorflow.demo.data.exception.TrackingUnavailableException;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TrackingEventProducerImpl implements TrackingEventProducer {
  private static final Logger log = LoggerFactory.getLogger(TrackingEventProducerImpl.class);
  private static final String OUTPUT_BINDING = "trackingEvents-out-0";

  private final StreamBridge streamBridge;

  @Override
  public void sendBestEffort(TrackingEventMqDto event) {
    try {
      if (!streamBridge.send(OUTPUT_BINDING, event)) {
        log.warn("Best-effort assigned event was dropped");
      }
    } catch (RuntimeException exception) {
      log.warn("Best-effort assigned event was dropped", exception);
    }
  }

  @Override
  public void sendDurably(List<TrackingEventMqDto> events) {
    if (events.isEmpty()) {
      return;
    }
    try {
      for (TrackingEventMqDto event : events) {
        if (!streamBridge.send(OUTPUT_BINDING, event)) {
          throw new TrackingUnavailableException("Tracking pipeline is unavailable");
        }
      }
    } catch (TrackingUnavailableException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new TrackingUnavailableException("Tracking pipeline is unavailable");
    }
  }
}
