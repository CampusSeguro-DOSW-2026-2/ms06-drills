package edu.escuelaing.campusseguro.events.security.local;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class LocalTokenService {

    private static final String ISSUER = "campusseguro-local";
    private static final Duration TOKEN_TTL = Duration.ofHours(8);

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String rolesClaim;

    public LocalTokenService(JwtEncoder jwtEncoder, Clock clock, @Value("${app.security.roles-claim:roles}") String rolesClaim) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.rolesClaim = rolesClaim;
    }

    public String generate(String subject, List<String> roles) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .claim(rolesClaim, roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
