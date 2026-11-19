package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Tests that JWT roles and permissions become authorities and unknown roles are ignored. */
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
    void mapsTheRolesAndKeepsTheOnesMs01Created() {
        JwtRoleConverter converter = new JwtRoleConverter("roles", "permissions");
        Jwt jwt = jwtWith("roles", List.of("COORDINADOR", "role_org", "ORG", "DESCONOCIDO", "  "));

        AbstractAuthenticationToken token = converter.convert(jwt);

        // DESCONOCIDO is well formed, so it travels like any role MS01 created; the abbreviations become the name
        assertEquals(Set.of("ROLE_COORDINADOR", "ROLE_ORGANIZADOR", "ROLE_DESCONOCIDO"), authorities(token));
        assertEquals("user-1", token.getName());
    }

    @Test
    void hasNoAuthoritiesWhenTheClaimIsMissing() {
        JwtRoleConverter converter = new JwtRoleConverter("roles", "permissions");

        AbstractAuthenticationToken token = converter.convert(jwtWith("roles", null));

        assertTrue(token.getAuthorities().isEmpty());
        assertEquals("user-1", token.getName());
    }

    @Test
    void readsTheConfiguredClaimName() {
        JwtRoleConverter converter = new JwtRoleConverter("grupos", "permissions");

        AbstractAuthenticationToken token = converter.convert(jwtWith("grupos", List.of("AUDITOR")));

        assertEquals(Set.of("ROLE_AUDITOR"), authorities(token));
    }

    @Test
    void permissionsAreKeptAsTheyComeAndBlankOnesAreIgnored() {
        JwtRoleConverter converter = new JwtRoleConverter("roles", "permissions");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user-1")
                .claim("roles", List.of("AUDITOR"))
                .claim("permissions", List.of("ms06:drill:read", " ms06:real_event:read ", "ms06:drill:read", " "))
                .build();

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertEquals(Set.of("ROLE_AUDITOR", "ms06:drill:read", "ms06:real_event:read"), authorities(token));
    }

    @Test
    void readsTheConfiguredPermissionsClaimName() {
        JwtRoleConverter converter = new JwtRoleConverter("roles", "permisos");

        AbstractAuthenticationToken token = converter.convert(jwtWith("permisos", List.of("ms06:drill:manage")));

        assertEquals(Set.of("ms06:drill:manage"), authorities(token));
    }
}
