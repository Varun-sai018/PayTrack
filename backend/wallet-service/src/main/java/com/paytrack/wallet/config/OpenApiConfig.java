package com.paytrack.wallet.config;

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
                        .title("Wallet Service")
                        .description("PayTrack Wallet Service")
                        .version("1.0.0"));
    }
}