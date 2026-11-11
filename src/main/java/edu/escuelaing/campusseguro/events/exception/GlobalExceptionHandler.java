package edu.escuelaing.campusseguro.events.exception;

import edu.escuelaing.campusseguro.events.client.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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
 * Turns any exception into an {@link ErrorResponse} (API standards, section 7.1) with an {@link ErrorCode}.
 * Domain exceptions are added here together with the story that uses them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** A business rule was broken: answers with the status and code of the exception. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(
            BusinessException ex,
            HttpServletRequest request) {

        return respond(
                ex.code(),
                ex.getMessage(),
                CorrelationIds.of(request),
                List.of());
    }

    /** A field breaks a business rule: 400 with the field as detail. */
    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<Object> handleInvalidField(
            InvalidFieldException ex,
            HttpServletRequest request) {

        return respond(
                ex.code(),
                ex.getMessage(),
                CorrelationIds.of(request),
                List.of(new ErrorResponse.Detail(ex.field(), ex.getMessage())));
    }

    /** A request uses ids that other services do not know: 400 with the field as detail. */
    @ExceptionHandler(InvalidReferenceException.class)
    public ResponseEntity<Object> handleInvalidReference(
            InvalidReferenceException ex,
            HttpServletRequest request) {

        return respond(
                ex.code(),
                ex.getMessage(),
                CorrelationIds.of(request),
                List.of(new ErrorResponse.Detail(ex.field(), "Valores desconocidos o inactivos: " + ex.invalidIds())));
    }

    /** Two users changed the same record at the same time (optimistic locking): 409. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLocking(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {

        return respond(
                ErrorCode.CONCURRENT_MODIFICATION,
                ErrorCode.CONCURRENT_MODIFICATION.defaultMessage(),
                CorrelationIds.of(request),
                List.of());
    }

    /** A database constraint was violated (unique, foreign key, etc.): 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.warn("Data integrity violation at {}", request.getRequestURI(), ex);

        return respond(
                ErrorCode.CONFLICT,
                ErrorCode.CONFLICT.defaultMessage(),
                CorrelationIds.of(request),
                List.of());
    }

    /** A parameter or bean validation outside the request body failed: 400 with field details. */
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
                CorrelationIds.of(request),
                details);
    }

    /** The user has no permission for the operation (e.g. a {@code @PreAuthorize} failed): 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        return respond(
                ErrorCode.FORBIDDEN,
                ErrorCode.FORBIDDEN.defaultMessage(),
                CorrelationIds.of(request),
                List.of());
    }

    /** Authentication is missing or invalid: 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {

        return respond(
                ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.defaultMessage(),
                CorrelationIds.of(request),
                List.of());
    }

    /** Any unexpected error: 500 without exposing internal details (it is logged). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error at {}", request.getRequestURI(), ex);

        return respond(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.defaultMessage(),
                CorrelationIds.of(request),
                List.of());
    }

    /** The request body failed {@code @Valid}: 400 with one detail per field. */
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
                correlationOf(request),
                details);
    }

    /** The body is not valid JSON or has wrong types: 400. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return respond(
                ErrorCode.VALIDATION_ERROR,
                "El cuerpo de la solicitud falta, está mal formado o tiene valores no válidos",
                correlationOf(request),
                List.of());
    }

    /** A path or query parameter has the wrong type (e.g. text instead of a number): 400. */
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
                new ErrorResponse.Detail(field, "El valor enviado no es válido"));

        return respond(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                correlationOf(request),
                details);
    }

    /** Common point for Spring MVC HTTP errors (404, 405, 415, ...): returns them in the standard format. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ErrorCode code = codeFor(statusCode);

        ErrorResponse response = new ErrorResponse(
                statusCode.value(),
                code.codigo(),
                code.defaultMessage(),
                List.of(),
                correlationOf(request));

        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(response);
    }

    /** Maps an HTTP status to its {@link ErrorCode}. */
    private ErrorCode codeFor(HttpStatusCode status) {
        return ErrorCode.fromStatus(status.value());
    }

    /** Correlation id of the request, to repeat it in the error body. */
    private String correlationOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? CorrelationIds.of(servletRequest.getRequest())
                : CorrelationIds.current();
    }

    private ResponseEntity<Object> respond(
            ErrorCode code,
            String message,
            String correlationId,
            List<ErrorResponse.Detail> errors) {

        ErrorResponse response = ErrorResponse.of(code, message, errors, correlationId);

        return ResponseEntity.status(code.status()).body(response);
    }
}
