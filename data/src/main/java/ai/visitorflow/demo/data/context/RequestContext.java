package ai.visitorflow.demo.data.context;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestContext {
  private final Long tenantId;
  private final Long userId;
  private final String visitorId;
  private final Long anonVisitorId;
  private final String correlationId;
}
