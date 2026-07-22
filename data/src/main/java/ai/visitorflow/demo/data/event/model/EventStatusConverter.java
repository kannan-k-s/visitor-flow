package ai.visitorflow.demo.data.event.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
class EventStatusConverter implements AttributeConverter<EventStatus, String> {
  @Override
  public String convertToDatabaseColumn(EventStatus status) {
    return status == null ? null : status.value();
  }

  @Override
  public EventStatus convertToEntityAttribute(String value) {
    return value == null ? null : EventStatus.fromValue(value);
  }
}
