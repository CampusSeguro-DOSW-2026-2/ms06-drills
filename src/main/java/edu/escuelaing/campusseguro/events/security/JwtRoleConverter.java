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
 * Class that converts the JWT in an authentication which permissions came form the roles claim
 */
@Component
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String rolesClaim;

    public JwtRoleConverter(@Value("${app.security.roles-claim:roles}") String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

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
