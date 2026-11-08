package edu.escuelaing.campusseguro.events.client.identity;

import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.client.ValidationCalls;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link IdentityGateway}: uses the same validation endpoint that MS03 uses with MS01
 * ({@code POST /api/v1/catalogo/validar-destinatarios}), asking about roles.
 */
@Component
@Profile("!local & !test")
public class RestIdentityGateway implements IdentityGateway {

    private static final String SERVICE = "MS01";
    private static final String FIELD = "participantRoles";

    private final RestClient client;
    private final String validateRecipientsPath;

    public RestIdentityGateway(
            RestClientFactory factory,
            @Value("${app.clients.ms01-url:http://localhost:8081}") String baseUrl,
            @Value("${app.clients.ms01.validate-recipients-path:/api/v1/catalogo/validar-destinatarios}")
                    String validateRecipientsPath) {
        this.client = factory.create(baseUrl);
        this.validateRecipientsPath = validateRecipientsPath;
    }

    @Override
    public void validateRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        List<Recipient> recipients = roles.stream().map(role -> new Recipient("ROLE", role.name())).toList();
        List<String> invalid = ValidationCalls.invalidIds(
                client, SERVICE, validateRecipientsPath, new Request(recipients));
        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(FIELD, invalid);
        }
    }

    record Recipient(String tipo, String id) {
    }

    record Request(List<Recipient> destinatarios) {
    }
}
