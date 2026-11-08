package edu.escuelaing.campusseguro.events.config;

import edu.escuelaing.campusseguro.events.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API description for Swagger UI: title, Bearer (JWT) security scheme and the
 * {@code ErrorResponse} schema used by all error responses.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    /** General API information and Bearer JWT authentication scheme. */
    @Bean
    OpenAPI campusSeguroEventsApi() {

        var schemas = ModelConverters.getInstance().read(ErrorResponse.class);

        return new OpenAPI()
                .info(new Info()
                        .title("MS06 - Eventos")
                        .version("1.0.0")
                        .description("API de gestión de eventos y simulacros de CampusSeguro: "
                                + "creación, programación, ejecución, finalización y evaluación"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSchemas(
                                "ErrorResponse",
                                schemas.get("ErrorResponse")))
                .addSecurityItem(
                        new SecurityRequirement().addList(BEARER_AUTH));
    }

    /** Documents the {@code ErrorResponse} body on every non-2xx response. */
    @Bean
    OpenApiCustomizer errorResponsesCustomizer() {
        return openApi -> {
            Schema<?> errorSchema = new Schema<>()
                    .$ref("#/components/schemas/ErrorResponse");
            Content errorContent = new Content().addMediaType(
                    "application/json",
                    new MediaType().schema(errorSchema));

            openApi.getPaths().values().forEach(path ->
                    path.readOperations().forEach(operation ->
                            operation.getResponses().forEach(
                                    (status, response) -> {
                                        if (!status.startsWith("2")) {
                                            response.setContent(errorContent);
                                        }
                                    })));
        };
    }
}
