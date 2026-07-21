package ai.visitorflow.demo.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {
  @Bean
  OpenAPI serviceOpenApi() {
    return new OpenAPI().info(new Info()
      .title("Experimentation and Assignment Service")
      .version("v1")
      .description("Deterministic assignment, tracking, configuration, and results APIs."));
  }
}
