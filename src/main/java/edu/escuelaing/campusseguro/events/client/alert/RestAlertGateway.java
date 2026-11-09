package edu.escuelaing.campusseguro.events.client.alert;

import edu.escuelaing.campusseguro.events.client.CorrelationIds;
import edu.escuelaing.campusseguro.events.client.ExternalCalls;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.IdentityHeaders;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link AlertGateway}. Every call goes through the API Gateway with the MS06 service key
 * ({@code X-Service-Key}) and, when it acts for a user, the user id and roles
 * ({@code X-User-Id}/{@code X-User-Role}) received in the request.
 *
 * <p>It uses the MS03 contract {@code POST /api/v1/alertas},
 * {@code PATCH /api/v1/alertas/{id}/programar}, {@code PATCH /api/v1/alertas/{id}/archivar}
 * and {@code PATCH /api/v1/alertas/{id}/activar}. An alert carries a {@code purpose} so several
 * live alerts can share the same simulation id (the event id).
 */
@Component
@Profile("!local & !test")
public class RestAlertGateway implements AlertGateway {

    private static final String SERVICE = "MS03";

    private final RestClient client;
    private final String serviceKey;
    private final String systemUserId;
    private final String systemUserRole;
    private final String alertsPath;
    private final String reschedulePath;
    private final String archivePath;
    private final String activatePath;

    public RestAlertGateway(
            RestClientFactory factory,
            @Value("${app.gateway.url:http://localhost:8080}") String baseUrl,
            @Value("${app.gateway.service-key:}") String serviceKey,
            @Value("${app.gateway.system-user-id:}") String systemUserId,
            @Value("${app.gateway.system-user-role:ORGANIZADOR}") String systemUserRole,
            @Value("${app.gateway.alerts-path:/api/v1/alertas}") String alertsPath,
            @Value("${app.gateway.reschedule-path:/api/v1/alertas/{id}/programar}") String reschedulePath,
            @Value("${app.gateway.archive-path:/api/v1/alertas/{id}/archivar}") String archivePath,
            @Value("${app.gateway.activate-path:/api/v1/alertas/{id}/activar}") String activatePath) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException(
                    "app.gateway.service-key is required (GATEWAY_SERVICE_KEY)");
        }
        this.client = factory.createGateway(baseUrl);
        this.serviceKey = serviceKey.strip();
        this.systemUserId = systemUserId == null ? "" : systemUserId.strip();
        this.systemUserRole = systemUserRole == null ? "ORGANIZADOR" : systemUserRole.strip();
        this.alertsPath = alertsPath;
        this.reschedulePath = reschedulePath;
        this.archivePath = archivePath;
        this.activatePath = activatePath;
    }

    @Override
    public String createAlert(AlertCommand command, AlertActor actor) {
        CreatedAlert created = ExternalCalls.call(SERVICE, () -> client.post()
                .uri(alertsPath)
                .headers(headers -> addHeaders(headers, actor))
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
    public void reschedule(String alertId, Instant activationAt, AlertActor actor) {
        ExternalCalls.run(SERVICE, () -> client.patch()
                .uri(reschedulePath, alertId)
                .headers(headers -> addHeaders(headers, actor))
                .body(new Schedule(activationAt))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void archive(String alertId, AlertActor actor) {
        ExternalCalls.run(SERVICE, () -> client.patch()
                .uri(archivePath, alertId)
                .headers(headers -> addHeaders(headers, actor))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void activate(String alertId, AlertActor actor) {
        ExternalCalls.run(SERVICE, () -> client.patch()
                .uri(activatePath, alertId)
                .headers(headers -> addHeaders(headers, actor))
                .retrieve()
                .toBodilessEntity());
    }

    private void addHeaders(HttpHeaders headers, AlertActor actor) {
        ResolvedActor resolved = resolve(actor);
        headers.set(IdentityHeaders.SERVICE_KEY, serviceKey);
        headers.set(CorrelationIds.HEADER, CorrelationIds.current());
        headers.set(IdentityHeaders.USER_ID, resolved.userId());
        headers.set(IdentityHeaders.USER_ROLE, resolved.roles());
    }

    private ResolvedActor resolve(AlertActor actor) {
        String userId = actor == null ? null : actor.userId();
        if (!isUuid(userId)) {
            userId = systemUserId;
        }
        if (!isUuid(userId)) {
            throw new ExternalServiceUnavailableException(SERVICE, new IllegalStateException(
                    "No valid user identity to call MS03: use an event created by a user or "
                            + "configure GATEWAY_SYSTEM_USER_ID"));
        }
        Set<Role> roles = actor == null || actor.roles().isEmpty() ? systemRoles() : actor.roles();
        String roleList = roles.stream()
                .map(Role::name)
                .sorted()
                .collect(Collectors.joining(","));
        return new ResolvedActor(userId, roleList);
    }

    private Set<Role> systemRoles() {
        Set<Role> roles = java.util.Arrays.stream(systemUserRole.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(Role::fromClaim)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toSet());
        return roles.isEmpty() ? Set.of(Role.ORGANIZADOR) : roles;
    }

    private boolean isUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Body toBody(AlertCommand command) {
        List<ScopeBody> scopes = groupScopes(command.scope());
        List<RecipientBody> recipients = command.recipientRoles().stream()
                .map(role -> new RecipientBody("ROLE", role.name()))
                .toList();
        Origin origin = command.simulacro()
                ? new Origin("SIMULATION", command.eventId())
                : new Origin("MANUAL", null);
        List<String> types = command.alertTypes() == null || command.alertTypes().isEmpty()
                ? List.of("OTHER")
                : command.alertTypes();
        return new Body(
                types,
                command.title(),
                command.instructions(),
                scopes,
                recipients,
                origin,
                command.purpose().name(),
                command.activationAt(),
                command.activateImmediately());
    }

    private List<ScopeBody> groupScopes(List<ScopeRef> scope) {
        Map<ScopeType, List<String>> grouped = new LinkedHashMap<>();
        for (ScopeRef ref : scope) {
            grouped.computeIfAbsent(ref.type(), type -> new ArrayList<>()).add(ref.id());
        }
        return grouped.entrySet().stream()
                .map(entry -> new ScopeBody(entry.getKey().name(), entry.getValue()))
                .toList();
    }

    record ResolvedActor(String userId, String roles) {
    }

    record ScopeBody(String type, List<String> ids) {
    }

    record RecipientBody(String type, String id) {
    }

    record Origin(String type, UUID simulationId) {
    }

    record Body(
            List<String> types,
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
