package edu.escuelaing.campusseguro.events.config;

import edu.escuelaing.campusseguro.events.client.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reuses the {@code X-Correlation-ID} the Gateway sent (or creates one) and keeps it for the
 * outbound calls of the same request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String value = request.getHeader(CorrelationIds.HEADER);
        if (value == null || value.isBlank()) {
            value = UUID.randomUUID().toString();
        }
        CorrelationIds.set(value);
        response.setHeader(CorrelationIds.HEADER, value);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIds.clear();
        }
    }
}
