package com.dbs.mot.grc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central OpenAPI / Swagger UI metadata for the GRC ORL services.
 *
 * <p>Springdoc auto-generates the operation and schema documentation from the controllers
 * and DTOs; this bean only supplies the top-level API information shown at
 * {@code /swagger-ui.html} and {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI grcOrlOpenApi() {
        return new OpenAPI().info(new Info()
                .title("GRC ORL Services API")
                .description("REST APIs for Operational Risk Landscape (ORL): CSV maintenance of "
                        + "reference tables, landscape assessment generation, assessment details "
                        + "and callouts. All endpoints require the X-EGRC-UserId header.")
                .version("v2")
                .contact(new Contact().name("GRC MOT")));
    }
}
