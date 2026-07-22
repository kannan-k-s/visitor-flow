package ai.visitorflow.demo.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebMvcConfig implements WebMvcConfigurer {
  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix("/{tenant}", HandlerTypePredicate.forBasePackage(
      "ai.visitorflow.demo.admin", "ai.visitorflow.demo.visitor"
    ));
  }
}
