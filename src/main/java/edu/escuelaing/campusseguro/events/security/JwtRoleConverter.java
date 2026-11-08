package edu.escuelaing.campusseguro.events.security;

import java.util.Collection;
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
 * Converts the JWT into an authentication whose permissions come from the roles
 * claim. Values that are not a {@link Role} are ignored.
 */
@Component
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String rolesClaim;

    /** @param rolesClaim name of the JWT claim with the roles (default {@code roles}) */
    public JwtRoleConverter(@Value("${app.security.roles-claim:roles}") String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    /**
     * Converts the JWT into an authentication whose authorities are {@code ROLE_<ROLE>}.
     * Unknown values are ignored; the user name is the {@code sub} claim.
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> claimValues = jwt.getClaimAsStringList(rolesClaim);
        Collection<GrantedAuthority> authorities = claimValues == null
                ? List.of()
                : claimValues.stream()
                        .map(Role::fromClaim)
                        .flatMap(Optional::stream)
                        .distinct()
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(role.authority()))
                        .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
