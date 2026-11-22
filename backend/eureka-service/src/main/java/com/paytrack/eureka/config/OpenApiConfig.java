package com.paytrack.eureka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paytrackOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Eureka Service")
                        .description("PayTrack Service Discovery")
                        .version("1.0.0"));
    }
}