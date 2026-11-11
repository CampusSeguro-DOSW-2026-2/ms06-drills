package edu.escuelaing.campusseguro.events.exception;

import org.springframework.http.HttpStatus;

/**
 * Catalog of API error codes. Each code sets its HTTP status and a default
 * message. Domain codes (invalid transitions, event not editable, etc.) are
 * added together with the story that needs them.
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
    INVALID_STATE_TRANSITION(
            HttpStatus.CONFLICT,
            "The requested status change is not allowed"),
    EVENT_NOT_EDITABLE(
            HttpStatus.CONFLICT,
            "The event cannot be edited in its current status"),

    EVENT_NOT_ACTIVE(
            HttpStatus.CONFLICT,
            "The event is not in progress"),

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

    /**
     * Maps an HTTP status to its error code (used for errors that do not come from a business rule).
     * An unknown 4xx status is reported as a validation error and any 5xx as an internal error.
     */
    public static ErrorCode fromStatus(int httpStatus) {
        return switch (httpStatus) {
            case 400 -> VALIDATION_ERROR;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 503 -> EXTERNAL_SERVICE_UNAVAILABLE;
            default -> httpStatus >= 500 ? INTERNAL_ERROR : VALIDATION_ERROR;
        };
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
