package ai.visitorflow.demo.visitor.tracking.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ai.visitorflow.demo.data.event.model.EventEntity;
import ai.visitorflow.demo.visitor.tracking.mq.TrackingEventMqDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration.AccessLevel;
import org.modelmapper.convention.MatchingStrategies;

class TrackingMapperImplTest {
  @Test
  void mapsBrokerEventWithoutTreatingForeignKeysAsEntityId() {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.getConfiguration()
      .setFieldMatchingEnabled(true)
      .setFieldAccessLevel(AccessLevel.PRIVATE)
      .setMatchingStrategy(MatchingStrategies.STRICT);
    TrackingMapper mapper = new TrackingMapperImpl(modelMapper);
    Instant occurredAt = Instant.parse("2026-07-22T00:00:00Z");
    TrackingEventMqDto event = TrackingEventMqDto.builder()
      .tenantId(11L)
      .experimentId(22L)
      .variantId(33L)
      .anonVisitorId(44L)
      .status("exposed")
      .occurredAt(occurredAt)
      .degraded(false)
      .build();

    EventEntity entity = mapper.toEntity(event);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getTenantId()).isEqualTo(11L);
    assertThat(entity.getExperimentId()).isEqualTo(22L);
    assertThat(entity.getVariantId()).isEqualTo(33L);
    assertThat(entity.getAnonVisitorId()).isEqualTo(44L);
    assertThat(entity.getStatus()).isEqualTo("exposed");
    assertThat(entity.getOccurredAt()).isEqualTo(occurredAt);
  }
}
