package edu.escuelaing.campusseguro.events.security.local;

import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Local profile only: creates signed JWT tokens (HS256) with the requested roles,
 * to test the API without MS01. They last 8 hours.
 */
@Service
@Profile("local")
public class LocalTokenService {

    private static final String ISSUER = "campusseguro-local";
    private static final Duration TOKEN_TTL = Duration.ofHours(8);

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String rolesClaim;
    private final String permissionsClaim;

    public LocalTokenService(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${app.security.roles-claim:roles}") String rolesClaim,
            @Value("${app.security.permissions-claim:permissions}") String permissionsClaim) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.rolesClaim = rolesClaim;
        this.permissionsClaim = permissionsClaim;
    }

    /**
     * Creates a signed token.
     *
     * @param subject user identifier (goes in the {@code sub} claim)
     * @param roles   role names for the roles claim; can be empty. The permissions claim gets the
     *                MS06 permissions of those roles ({@link LocalPermissions})
     * @return the compact token, ready for the {@code Authorization: Bearer} header
     */
    public String generate(String subject, List<String> roles) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .claim(rolesClaim, roles)
                .claim(permissionsClaim, permissionsOf(roles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private List<String> permissionsOf(List<String> roles) {
        return roles.stream()
                .map(Role::fromClaim)
                .flatMap(Optional::stream)
                .flatMap(role -> LocalPermissions.of(role).stream())
                .distinct()
                .toList();
    }
}
