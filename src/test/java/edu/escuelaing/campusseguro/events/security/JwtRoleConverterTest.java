package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Class that test the JwtRoleConverter file
 * JwtRoleConverterTest
 */

class JwtRoleConverterTest {

    private Jwt jwtWith(String claimName, Object claimValue) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user-1");
        if (claimValue != null) {
            builder.claim(claimName, claimValue);
        }
        return builder.build();
    }

    private Set<String> authorities(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
    }

    @Test
    void mapsKnownRolesAndIgnoresTheRest() {
        JwtRoleConverter converter = new JwtRoleConverter("roles");
        Jwt jwt = jwtWith("roles", List.of("COORDINADOR", "role_org", "ORG", "DESCONOCIDO", "  "));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertEquals(Set.of("ROLE_COORDINADOR", "ROLE_ORGANIZADOR"), authorities(token));
        assertEquals("user-1", token.getName());
    }

    @Test
    void hasNoAuthoritiesWhenTheClaimIsMissing() {
        JwtRoleConverter converter = new JwtRoleConverter("roles");

        AbstractAuthenticationToken token = converter.convert(jwtWith("roles", null));

        assertTrue(token.getAuthorities().isEmpty());
        assertEquals("user-1", token.getName());
    }

    @Test
    void readsTheConfiguredClaimName() {
        JwtRoleConverter converter = new JwtRoleConverter("grupos");

        AbstractAuthenticationToken token = converter.convert(jwtWith("grupos", List.of("AUDITOR")));

        assertEquals(Set.of("ROLE_AUDITOR"), authorities(token));
    }
}
