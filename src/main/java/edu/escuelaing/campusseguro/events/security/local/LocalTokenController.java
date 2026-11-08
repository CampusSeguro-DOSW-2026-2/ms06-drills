package edu.escuelaing.campusseguro.events.security.local;

import edu.escuelaing.campusseguro.events.security.Role;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local only: gives one test token per role and one without roles.
 */
@Hidden
@RestController
@Profile("local")
@RequestMapping("/local/tokens")
public class LocalTokenController {

    /** Key of the token of an authenticated user with no roles. */
    static final String GENERIC_USER = "USER";

    private final LocalTokenService localTokenService;

    public LocalTokenController(LocalTokenService localTokenService) {
        this.localTokenService = localTokenService;
    }

    /** Returns a map role name → token, plus the key {@code USER} (no roles). */
    @GetMapping
    public Map<String, String> tokens() {
        Map<String, String> tokens = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            String subject = "local-" + role.name().toLowerCase(Locale.ROOT);
            tokens.put(role.name(), localTokenService.generate(subject, List.of(role.name())));
        }
        tokens.put(GENERIC_USER, localTokenService.generate("local-user", List.of()));
        return tokens;
    }
}
