package ai.visitorflow.demo.web.security;

public record JwtPrincipal(Long userId, Long tenantId, String email) {
}
