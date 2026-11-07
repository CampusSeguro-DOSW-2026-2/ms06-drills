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
 * Class that manages the not authorized access tries
 * RestAuthenticationEntryPoint
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    public RestAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        errorResponseWriter.write(request, response, ErrorCode.UNAUTHORIZED);
    }
}
