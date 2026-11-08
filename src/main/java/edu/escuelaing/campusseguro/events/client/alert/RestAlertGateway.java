package edu.escuelaing.campusseguro.events.client.alert;

import edu.escuelaing.campusseguro.events.client.ExternalCalls;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link AlertGateway}. It uses the MS03 alert API
 * ({@code POST /api/v1/alertas}, {@code PATCH /api/v1/alertas/{id}/programar}) and proposes two
 * changes the MS03 team has to accept: a {@code scopes} list (an event can mix buildings and
 * zones) and {@code PATCH /api/v1/alertas/{id}/borrador} to take an alert back to draft.
 */
@Component
@Profile("!local & !test")
public class RestAlertGateway implements AlertGateway {

    private static final String SERVICE = "MS03";

    private final RestClient client;
    private final String alertsPath;
    private final String reschedulePath;
    private final String draftPath;

    public RestAlertGateway(
            RestClientFactory factory,
            @Value("${app.clients.ms03-url:http://localhost:8080}") String baseUrl,
            @Value("${app.clients.ms03.alerts-path:/api/v1/alertas}") String alertsPath,
            @Value("${app.clients.ms03.reschedule-path:/api/v1/alertas/{id}/programar}") String reschedulePath,
            @Value("${app.clients.ms03.draft-path:/api/v1/alertas/{id}/borrador}") String draftPath) {
        this.client = factory.create(baseUrl);
        this.alertsPath = alertsPath;
        this.reschedulePath = reschedulePath;
        this.draftPath = draftPath;
    }

    @Override
    public String createAlert(AlertCommand command) {
        CreatedAlert created = ExternalCalls.call(SERVICE, () -> client.post()
                .uri(alertsPath)
                .body(toBody(command))
                .retrieve()
                .body(CreatedAlert.class));
        if (created == null || created.id() == null) {
            throw new ExternalServiceUnavailableException(
                    SERVICE, new IllegalStateException("ALE answered without an alert id"));
        }
        return created.id();
    }

    @Override
    public void reschedule(String alertId, Instant activationAt) {
        ExternalCalls.run(SERVICE, () -> client.patch()
                .uri(reschedulePath, alertId)
                .body(new Schedule(activationAt))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void backToDraft(String alertId) {
        ExternalCalls.run(SERVICE, () -> client.patch()
                .uri(draftPath, alertId)
                .retrieve()
                .toBodilessEntity());
    }

    private Body toBody(AlertCommand command) {
        Map<ScopeType, List<String>> grouped = new LinkedHashMap<>();
        for (ScopeRef ref : command.scope()) {
            grouped.computeIfAbsent(ref.type(), type -> new ArrayList<>()).add(ref.id());
        }
        List<ScopeBody> scopes = grouped.entrySet().stream()
                .map(entry -> new ScopeBody(entry.getKey().name(), entry.getValue()))
                .toList();
        List<RecipientBody> recipients = command.recipientRoles().stream()
                .map(role -> new RecipientBody("ROLE", role.name()))
                .toList();
        return new Body(
                command.simulacro() ? "SIMULATION" : "OTHER",
                command.title(),
                command.instructions(),
                scopes,
                recipients,
                new Origin("EVENT", command.eventId()),
                command.purpose().name(),
                command.activationAt(),
                command.activateImmediately());
    }

    record ScopeBody(String type, List<String> ids) {
    }

    record RecipientBody(String type, String id) {
    }

    record Origin(String type, UUID eventId) {
    }

    record Body(
            String type,
            String title,
            String instructions,
            List<ScopeBody> scopes,
            List<RecipientBody> recipients,
            Origin origin,
            String purpose,
            Instant activationAt,
            boolean activateImmediately) {
    }

    record Schedule(Instant activationAt) {
    }

    record CreatedAlert(String id) {
    }
}
