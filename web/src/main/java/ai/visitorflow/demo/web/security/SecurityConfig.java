package ai.visitorflow.demo.web.security;

import ai.visitorflow.demo.web.error.HttpErrorWriter;
import ai.visitorflow.demo.web.filter.RequestContextFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
    HttpSecurity http, JwtCookieAuthenticationFilter jwtFilter,
    RequestContextFilter contextFilter, TenantOAuthLoginFilter tenantLoginFilter,
    OAuthLoginSuccessHandler successHandler, TenantAuthorizationRequestResolver authorizationRequestResolver,
    HttpErrorWriter errorWriter
  ) throws Exception {
    http
      .csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
      .authorizeHttpRequests(authorize -> authorize
        .requestMatchers(
          "/*/v1/assign", "/*/v1/track", "/*/v1/auth/login", "/oauth2/**", "/login/**",
          "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health"
        ).permitAll()
        .anyRequest().authenticated())
      .oauth2Login(oauth -> oauth
        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
        .successHandler(successHandler))
      .exceptionHandling(errors -> errors
        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(
          response, HttpStatus.UNAUTHORIZED.value(), "unauthorized", "Authentication is required"
        ))
        .accessDeniedHandler((request, response, exception) -> errorWriter.write(
          response, HttpStatus.FORBIDDEN.value(), "forbidden", "Access is denied"
        )))
      .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
      .addFilterAfter(contextFilter, JwtCookieAuthenticationFilter.class)
      .addFilterBefore(tenantLoginFilter, OAuth2AuthorizationRequestRedirectFilter.class);
    return http.build();
  }

  @Bean
  FilterRegistrationBean<JwtCookieAuthenticationFilter> jwtFilterRegistration(
    JwtCookieAuthenticationFilter filter
  ) {
    return disabled(filter);
  }

  @Bean
  FilterRegistrationBean<RequestContextFilter> contextFilterRegistration(RequestContextFilter filter) {
    return disabled(filter);
  }

  @Bean
  FilterRegistrationBean<TenantOAuthLoginFilter> tenantLoginFilterRegistration(TenantOAuthLoginFilter filter) {
    return disabled(filter);
  }

  private <T extends OncePerRequestFilter> FilterRegistrationBean<T> disabled(T filter) {
    FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
