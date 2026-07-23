package com.ecommerce.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI v3 metadata. Springdoc auto-generates the endpoint documentation from
 * the controller annotations; this bean supplies the top-level API info shown at
 * {@code /swagger-ui.html} and {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderProcessingOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Order Processing System API")
                .description("Backend API for creating, tracking, and managing e-commerce orders.")
                .version("1.0.0")
                .contact(new Contact().name("Tushar Sharma"))
                .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")));
    }
}
