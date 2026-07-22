package ai.visitorflow.demo.data.identity;

public interface VisitorIdentityResolver {
  long resolveForAssign(Long tenantId, String visitorId, Long anonVisitorId);
  Long resolveForTrack(Long tenantId, String visitorId, Long anonVisitorId);
}
