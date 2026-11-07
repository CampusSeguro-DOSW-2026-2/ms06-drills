package edu.escuelaing.campusseguro.events.security.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import edu.escuelaing.campusseguro.events.security.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class LocalTokensTest {

    private static final SecretKey KEY = new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
    private final LocalTokenService service = new LocalTokenService(
            new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), Clock.systemUTC(), "roles");

    @Test
    void serviceIssuesATokenWithSubjectRolesAndEightHoursOfLife() {
        Jwt jwt = decoder.decode(service.generate("local-admin", List.of("ADMIN")));

        assertEquals("local-admin", jwt.getSubject());
        assertEquals(List.of("ADMIN"), jwt.getClaimAsStringList("roles"));
        assertEquals(Duration.ofHours(8), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    }

    @Test
    void controllerGivesOneTokenPerRolePlusAGenericUser() {
        LocalTokenController controller = new LocalTokenController(service);

        Map<String, String> tokens = controller.tokens();

        Set<String> expectedKeys = new HashSet<>();
        EnumSet.allOf(Role.class).forEach(role -> expectedKeys.add(role.name()));
        expectedKeys.add(LocalTokenController.GENERIC_USER);
        assertEquals(expectedKeys, tokens.keySet());

        Jwt coordinator = decoder.decode(tokens.get("COORDINADOR"));
        assertEquals("local-coordinador", coordinator.getSubject());
        assertEquals(List.of("COORDINADOR"), coordinator.getClaimAsStringList("roles"));

        Jwt generic = decoder.decode(tokens.get(LocalTokenController.GENERIC_USER));
        assertEquals("local-user", generic.getSubject());
        assertTrue(tokens.get(LocalTokenController.GENERIC_USER).length() > 20);
    }
}
