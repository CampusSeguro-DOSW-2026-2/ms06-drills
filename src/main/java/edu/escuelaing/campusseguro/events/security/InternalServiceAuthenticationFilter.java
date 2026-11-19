package edu.escuelaing.campusseguro.events.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the internal routes with the {@code X-Service-Name} header that the API Gateway
 * derives from the service key (Standards section 5).
 *
 * <p>The internal routes never use {@code X-User-*}: they are calls between services without a
 * user. When the header is missing the request stays unauthenticated and the entry point answers
 * {@code 401}; when the service is not in the allowed list it is authenticated without authorities
 * and the access denied handler answers {@code 403}.
 */
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    /** Header injected by the Gateway with the name of the calling service. */
    public static final String SERVICE_NAME_HEADER = "X-Service-Name";

    /** Authority that identifies an allowed internal service. */
    public static final String AUTHORITY = "SERVICE_MS03";

    private final Set<String> allowedServices;

    /**
     * Creates the filter with the list of services allowed on the internal route.
     *
     * @param allowedServices service names separated by commas
     */
    public InternalServiceAuthenticationFilter(String allowedServices) {
        this.allowedServices = Arrays.stream(allowedServices == null ? new String[0] : allowedServices.split(","))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(SERVICE_NAME_HEADER);
        if (header != null && !header.isBlank()) {
            String service = header.trim().toLowerCase(Locale.ROOT);
            List<GrantedAuthority> authorities = allowedServices.contains(service)
                    ? List.of(new SimpleGrantedAuthority(AUTHORITY))
                    : List.of();
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(service, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        filterChain.doFilter(request, response);
    }
}
