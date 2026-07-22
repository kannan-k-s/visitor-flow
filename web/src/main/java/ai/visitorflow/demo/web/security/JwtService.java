package ai.visitorflow.demo.web.security;

public interface JwtService {
  String mint(Long userId, Long tenantId, String email);
  JwtPrincipal parse(String token);
  String mintOAuthState(String tenantName);
  String parseOAuthState(String token);
}
