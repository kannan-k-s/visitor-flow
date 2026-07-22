package ai.visitorflow.demo.data.experiment.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum AssignmentStrategy {
  HASH("hash"),
  SWRR("swrr");

  private final String value;

  AssignmentStrategy(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static AssignmentStrategy fromValue(String value) {
    return Arrays.stream(values())
      .filter(strategy -> strategy.value.equals(value))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unsupported assignment strategy: " + value));
  }
}
