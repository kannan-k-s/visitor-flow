package ai.visitorflow.demo.visitor.tracking.config;

import ai.visitorflow.demo.visitor.tracking.consumer.TrackingEventConsumer;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TrackingProperties.class)
@RequiredArgsConstructor
class TrackingConfig {
  private final TrackingEventConsumer trackingEventConsumer;

  @Bean
  Consumer<TrackingEventMqDto> trackingEvents() {
    return trackingEventConsumer::consume;
  }
}
