package edu.escuelaing.campusseguro.events.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Class that translates an exception in a {@link ErrorResponse} with a {@link ErrorCode}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLocking(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {

        return respond(
                ErrorCode.CONCURRENT_MODIFICATION,
                ErrorCode.CONCURRENT_MODIFICATION.defaultMessage(),
                request.getRequestURI(),
                List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.warn("Data integrity violation at {}", request.getRequestURI(), ex);

        return respond(
                ErrorCode.CONFLICT,
                ErrorCode.CONFLICT.defaultMessage(),
                request.getRequestURI(),
                List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        List<ErrorResponse.Detail> details =
                ex.getConstraintViolations().stream()
                        .map(violation -> new ErrorResponse.Detail(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()))
                        .toList();

        return respond(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request.getRequestURI(),
                details);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        return respond(
                ErrorCode.FORBIDDEN,
                ErrorCode.FORBIDDEN.defaultMessage(),
                request.getRequestURI(),
                List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {

        return respond(
                ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.defaultMessage(),
                request.getRequestURI(),
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error at {}", request.getRequestURI(), ex);

        return respond(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.defaultMessage(),
                request.getRequestURI(),
                List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ErrorResponse.Detail> details = new ArrayList<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(error -> details.add(
                        new ErrorResponse.Detail(
                                error.getField(),
                                error.getDefaultMessage())));

        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> details.add(
                        new ErrorResponse.Detail(
                                error.getObjectName(),
                                error.getDefaultMessage())));

        return respond(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                pathOf(request),
                details);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return respond(
                ErrorCode.VALIDATION_ERROR,
                "The request body is missing, malformed, or contains invalid values",
                pathOf(request),
                List.of());
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String field =
                ex instanceof MethodArgumentTypeMismatchException mismatch
                        ? mismatch.getName()
                        : ex.getPropertyName();

        List<ErrorResponse.Detail> details = List.of(
                new ErrorResponse.Detail(field, "The supplied value is invalid"));

        return respond(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                pathOf(request),
                details);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ErrorCode code = codeFor(statusCode);

        ErrorResponse response = new ErrorResponse(
                clock.instant(),
                statusCode.value(),
                code.name(),
                code.defaultMessage(),
                pathOf(request),
                List.of());

        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(response);
    }

    private ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.CONFLICT;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 503 -> ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE;
            default -> status.is5xxServerError()
                    ? ErrorCode.INTERNAL_ERROR
                    : ErrorCode.VALIDATION_ERROR;
        };
    }

    private String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : "";
    }

    private ResponseEntity<Object> respond(
            ErrorCode code,
            String message,
            String path,
            List<ErrorResponse.Detail> details) {

        ErrorResponse response = ErrorResponse.of(
                clock.instant(),
                code,
                message,
                path,
                details);

        return ResponseEntity.status(code.status()).body(response);
    }
}
