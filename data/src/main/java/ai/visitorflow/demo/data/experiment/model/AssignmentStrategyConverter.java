package ai.visitorflow.demo.data.experiment.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
class AssignmentStrategyConverter implements AttributeConverter<AssignmentStrategy, String> {
  @Override
  public String convertToDatabaseColumn(AssignmentStrategy strategy) {
    return strategy == null ? null : strategy.value();
  }

  @Override
  public AssignmentStrategy convertToEntityAttribute(String value) {
    return value == null ? null : AssignmentStrategy.fromValue(value);
  }
}
