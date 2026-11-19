package edu.escuelaing.campusseguro.events.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
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
 * the Gateway validates it and forwards {@code X-User-Id}, {@code X-User-Role} and
 * {@code X-User-Permission}.
 *
 * <p>The permissions decide what the user may do; the roles are only used to know which events
 * invite the user. A role that MS06 does not know (MS01 lets the admin create roles) is ignored.
 * If the user id is not a UUID or the permissions are missing, the request is rejected with the
 * standard 401 response.
 */
@Component
@Profile("!local & !test")
public class IdentityHeadersAuthenticationFilter extends OncePerRequestFilter {

    private static final String ROLE_PREFIX = "ROLE_";

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
        String permissions = request.getHeader(IdentityHeaders.USER_PERMISSION);

        if (isBlank(userId) && isBlank(roles) && isBlank(permissions)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isUuid(userId) || isBlank(permissions)) {
            reject(request, response);
            return;
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        if (!isBlank(roles)) {
            Arrays.stream(roles.split(","))
                    .map(String::strip)
                    .filter(value -> !value.isEmpty())
                    .map(this::roleAuthority)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .distinct()
                    .forEach(authority -> authorities.add(new SimpleGrantedAuthority(authority)));
        }
        List<String> codes = Arrays.stream(permissions.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            reject(request, response);
            return;
        }
        codes.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(userId, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /** Authority of a role name: the official ones keep their prefix, the ones of MS01 travel as they are. */
    private Optional<String> roleAuthority(String value) {
        Optional<Role> known = Role.fromClaim(value);
        if (known.isPresent()) {
            return Optional.of(known.get().authority());
        }
        String normalized = RoleNames.normalize(value);
        return RoleNames.hasStoredFormat(normalized) ? Optional.of(ROLE_PREFIX + normalized) : Optional.empty();
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
