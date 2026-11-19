package edu.escuelaing.campusseguro.events.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Converts the JWT into an authentication: roles from the roles claim (as {@code ROLE_<ROLE>},
 * values that are not a {@link Role} are ignored) and permissions from the permissions claim
 * (as they are, for example {@code ms06:drill:read}). Only used in the local and test profiles;
 * behind the Gateway the permissions arrive in the {@code X-User-Permission} header.
 */
@Component
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String rolesClaim;
    private final String permissionsClaim;

    /**
     * @param rolesClaim       name of the JWT claim with the roles (default {@code roles})
     * @param permissionsClaim name of the JWT claim with the permissions (default {@code permissions})
     */
    public JwtRoleConverter(
            @Value("${app.security.roles-claim:roles}") String rolesClaim,
            @Value("${app.security.permissions-claim:permissions}") String permissionsClaim) {
        this.rolesClaim = rolesClaim;
        this.permissionsClaim = permissionsClaim;
    }

    /**
     * Converts the JWT into an authentication whose authorities are {@code ROLE_<ROLE>} for the
     * roles and the permission codes as they come. Unknown roles are ignored; the user name is
     * the {@code sub} claim.
     */
    /** Authority of a role name: the official ones keep their prefix, the ones of MS01 travel as they are. */
    private Optional<String> roleAuthority(String value) {
        Optional<Role> known = Role.fromClaim(value);
        if (known.isPresent()) {
            return Optional.of(known.get().authority());
        }
        String normalized = RoleNames.normalize(value);
        return RoleNames.hasStoredFormat(normalized) ? Optional.of("ROLE_" + normalized) : Optional.empty();
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> claimValues = jwt.getClaimAsStringList(rolesClaim);
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (claimValues != null) {
            claimValues.stream()
                    .map(this::roleAuthority)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .distinct()
                    .forEach(authority -> authorities.add(new SimpleGrantedAuthority(authority)));
        }
        List<String> permissions = jwt.getClaimAsStringList(permissionsClaim);
        if (permissions != null) {
            permissions.stream()
                    .map(String::strip)
                    .filter(code -> !code.isEmpty())
                    .distinct()
                    .forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
