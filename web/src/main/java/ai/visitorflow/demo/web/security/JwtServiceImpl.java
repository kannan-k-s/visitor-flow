package ai.visitorflow.demo.web.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
class JwtServiceImpl implements JwtService {
  private final SecurityProperties properties;
  private final JwtEncoder encoder;
  private final JwtDecoder decoder;

  JwtServiceImpl(SecurityProperties properties) {
    byte[] secret = properties.jwtSigningSecret().getBytes(StandardCharsets.UTF_8);
    if (secret.length < 32) {
      throw new IllegalArgumentException("JWT signing secret must contain at least 32 bytes");
    }
    SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
    this.properties = properties;
    this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Override
  public String mint(Long userId, Long tenantId, String email) {
    Instant issuedAt = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
      .issuer("demo")
      .issuedAt(issuedAt)
      .expiresAt(issuedAt.plus(properties.jwtTtl()))
      .subject(String.valueOf(userId))
      .claim("tenant_id", tenantId)
      .claim("email", email)
      .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Override
  public JwtPrincipal parse(String token) {
    Jwt jwt = decoder.decode(token);
    return new JwtPrincipal(
      Long.valueOf(jwt.getSubject()), jwt.getClaim("tenant_id"), jwt.getClaimAsString("email")
    );
  }

  @Override
  public String mintOAuthState(String tenantName) {
    Instant issuedAt = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
      .issuer("assignment-service")
      .issuedAt(issuedAt)
      .expiresAt(issuedAt.plus(properties.oauthStateTtl()))
      .subject(UUID.randomUUID().toString())
      .claim("purpose", "oauth_state")
      .claim("tenant", tenantName)
      .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Override
  public String parseOAuthState(String token) {
    Jwt jwt = decoder.decode(token);
    if (!"oauth_state".equals(jwt.getClaimAsString("purpose"))) {
      throw new IllegalArgumentException("Invalid OAuth state purpose");
    }
    String tenantName = jwt.getClaimAsString("tenant");
    if (tenantName == null || tenantName.isBlank()) {
      throw new IllegalArgumentException("OAuth state does not contain a tenant");
    }
    return tenantName;
  }
}
