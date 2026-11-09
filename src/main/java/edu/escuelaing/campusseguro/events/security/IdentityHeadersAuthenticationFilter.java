package edu.escuelaing.campusseguro.events.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request with the headers the API Gateway injects. MS06 never receives the JWT:
 * the Gateway validates it and forwards {@code X-User-Id} and {@code X-User-Role}.
 *
 * <p>If only one of the two headers arrives, or the id is not a UUID, the request is rejected
 * with the standard 401 response.
 */
@Component
@Profile("!local & !test")
public class IdentityHeadersAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public IdentityHeadersAuthenticationFilter(RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(IdentityHeaders.USER_ID);
        String roles = request.getHeader(IdentityHeaders.USER_ROLE);

        if (isBlank(userId) && isBlank(roles)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isUuid(userId) || isBlank(roles)) {
            reject(request, response);
            return;
        }

        List<GrantedAuthority> authorities = Arrays.stream(roles.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(Role::fromClaim)
                .flatMap(Optional::stream)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();

        if (authorities.isEmpty()) {
            reject(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(userId, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, null);
    }

    private boolean isUuid(String value) {
        return value != null && UUID.matcher(value).matches();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
