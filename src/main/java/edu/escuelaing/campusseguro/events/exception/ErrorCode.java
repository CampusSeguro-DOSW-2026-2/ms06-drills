package edu.escuelaing.campusseguro.events.exception;

import org.springframework.http.HttpStatus;

/**
 * Catálogo de códigos de error de la API. Cada código fija su estado HTTP y
 * un mensaje por defecto. Los códigos propios del dominio (transiciones
 * inválidas, evento no editable, etc.) se añaden junto con la historia que
 * los necesita.
 */
public enum ErrorCode {

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "The submitted data is invalid"),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Authentication is required or the token is invalid"),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "You do not have permission to perform this operation"),

    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "The requested resource was not found"),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "The HTTP method is not allowed"),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "The content type is not supported"),

    CONFLICT(
            HttpStatus.CONFLICT,
            "The operation conflicts with the current state of the resource"),
    CONCURRENT_MODIFICATION(
            HttpStatus.CONFLICT,
            "The resource was modified by another user"),

    EXTERNAL_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "An external service is temporarily unavailable. Please try again later"),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
