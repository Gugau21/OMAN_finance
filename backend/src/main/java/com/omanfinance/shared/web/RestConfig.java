package com.omanfinance.shared.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the REST API, from {@code app.cors.allowed-origins}. */
@Configuration
public class RestConfig implements WebMvcConfigurer {

  private final String[] allowedOrigins;

  public RestConfig(@Value(AllowedOrigins.PROPERTY) String allowedOrigins) {
    this.allowedOrigins = AllowedOrigins.parse(allowedOrigins);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }
}
