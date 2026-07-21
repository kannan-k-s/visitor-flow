package ai.visitorflow.demo.data.event.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum EventStatus {
  ASSIGNED("assigned", false),
  EXPOSED("exposed", true),
  CONVERTED("converted", true);

  private final String value;
  private final boolean clientReportable;

  EventStatus(String value, boolean clientReportable) {
    this.value = value;
    this.clientReportable = clientReportable;
  }

  @JsonValue
  public String value() {
    return value;
  }

  public boolean isClientReportable() {
    return clientReportable;
  }

  @JsonCreator
  public static EventStatus fromValue(String value) {
    return Arrays.stream(values())
      .filter(status -> status.value.equals(value))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unsupported tracking status: " + value));
  }
}
