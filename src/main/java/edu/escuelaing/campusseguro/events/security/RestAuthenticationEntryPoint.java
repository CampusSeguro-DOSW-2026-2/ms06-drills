package edu.escuelaing.campusseguro.events.security;

import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.exception.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers 401 with the {@code ErrorResponse} format when a request has no valid
 * credentials (missing, expired or badly signed token).
 *
 * <p>It runs inside the security filter chain, before the controllers, so
 * {@code GlobalExceptionHandler} does not see this case.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    public RestAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    /** Writes the standard 401 response. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        errorResponseWriter.write(request, response, ErrorCode.UNAUTHORIZED);
    }
}
