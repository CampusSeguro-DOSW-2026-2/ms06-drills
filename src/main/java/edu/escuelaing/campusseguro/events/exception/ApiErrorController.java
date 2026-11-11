package edu.escuelaing.campusseguro.events.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers the {@code /error} path with the standard {@link ErrorResponse} JSON.
 *
 * <p>Spring Boot forwards to {@code /error} the errors that never reach a controller (for example
 * a failure inside a filter or the web server). Without this class they would come out in Boot's
 * own format or as plain text, and the API Gateway turns a plain-text error into a 502.
 */
@RestController
public class ApiErrorController implements ErrorController {

    private final Clock clock;

    /** @param clock application clock, for the timestamp of the error */
    public ApiErrorController(Clock clock) {
        this.clock = clock;
    }

    /** Builds the error body from the status and path the servlet container stored in the request. */
    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer value ? value : 500;
        ErrorCode code = ErrorCode.fromStatus(status);

        Object pathAttribute = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = pathAttribute == null ? request.getRequestURI() : pathAttribute.toString();

        ErrorResponse body = new ErrorResponse(
                clock.instant(), status, code.name(), code.defaultMessage(), path, List.of());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
