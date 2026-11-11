package edu.escuelaing.campusseguro.events.client.checkin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.escuelaing.campusseguro.events.client.CorrelationIds;
import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.ExternalCalls;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.StatusLabels;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.IdentityHeaders;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link CheckInGateway}. Every call goes through the API Gateway with the MS06 service key
 * ({@code X-Service-Key}); nothing is sent directly to MS04.
 *
 * <p>Internal MS04 routes (authorized by the calling service, not by user role):
 * {@code PUT /api/v1/chi/eventos/{id}/estado} and {@code GET /api/v1/chi/eventos/{id}/checkins}.
 *
 * <p>The check-in numbers come from {@code GET /api/v1/chi/eventos/{id}/consolidado}. That route is
 * a user route in MS04, so it is called with the system user ({@code X-User-Id} and
 * {@code X-User-Role}); the role needs the MS04 dashboard permission (ORGANIZADOR has it).
 * The consolidated answer has no participation percentage, so that value is left empty.
 */
@Component
@Profile("!local & !test")
public class RestCheckInGateway implements CheckInGateway {

    private static final String SERVICE = "MS04";
    private static final String SERVICE_NAME = "ms06";

    private final RestClient client;
    private final String serviceKey;
    private final String systemUserId;
    private final String systemUserRole;
    private final String statePath;
    private final String consolidatedPath;
    private final String checkinsPath;

    public RestCheckInGateway(
            RestClientFactory factory,
            @Value("${app.gateway.url:http://localhost:8080}") String baseUrl,
            @Value("${app.gateway.service-key:}") String serviceKey,
            @Value("${app.gateway.system-user-id:}") String systemUserId,
            @Value("${app.gateway.system-user-role:ORGANIZADOR}") String systemUserRole,
            @Value("${app.clients.ms04.state-path:/api/v1/chi/eventos/{eventId}/estado}") String statePath,
            @Value("${app.clients.ms04.consolidated-path:/api/v1/chi/eventos/{eventId}/consolidado}")
                    String consolidatedPath,
            @Value("${app.clients.ms04.checkins-path:/api/v1/chi/eventos/{eventId}/checkins}")
                    String checkinsPath) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("app.gateway.service-key is required (GATEWAY_SERVICE_KEY)");
        }
        this.client = factory.createGateway(baseUrl);
        this.serviceKey = serviceKey.strip();
        this.systemUserId = systemUserId == null ? "" : systemUserId.strip();
        this.systemUserRole = systemUserRole == null || systemUserRole.isBlank()
                ? Role.ORGANIZADOR.name()
                : systemUserRole.strip();
        this.statePath = statePath;
        this.consolidatedPath = consolidatedPath;
        this.checkinsPath = checkinsPath;
    }

    @Override
    public void pushState(EventStatePayload payload) {
        ExternalCalls.run(SERVICE, () -> client.put()
                .uri(statePath, payload.eventId())
                .headers(this::addServiceHeaders)
                .body(toBody(payload))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public CheckInIndicators indicators(UUID eventId) {
        requireSystemUser();
        ConsolidatedBody body = ExternalCalls.call(SERVICE, () -> client.get()
                .uri(consolidatedPath, eventId)
                .headers(this::addUserHeaders)
                .retrieve()
                .body(ConsolidatedBody.class));
        if (body == null) {
            throw new ExternalServiceUnavailableException(SERVICE, new IllegalStateException("Empty answer"));
        }
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        if (body.totalesPorEstado() != null) {
            body.totalesPorEstado().forEach((status, total) -> byStatus.put(status, Math.toIntExact(total)));
        }
        // The consolidated answer has no participation percentage: it stays empty (null).
        return new CheckInIndicators(
                eventId, body.calculadoEn(), Math.toIntExact(body.totalReportados()), byStatus, null);
    }

    @Override
    public CheckInPage checkins(UUID eventId, String status, int page, int size) {
        PageBody body = ExternalCalls.call(SERVICE, () -> client.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(checkinsPath);
                    if (status != null && !status.isBlank()) {
                        uriBuilder.queryParam("estado", status);
                    }
                    return uriBuilder.queryParam("page", page).queryParam("size", size).build(eventId);
                })
                .headers(this::addServiceHeaders)
                .retrieve()
                .body(PageBody.class));
        if (body == null) {
            return new CheckInPage(page, size, 0, List.of());
        }
        List<CheckInItem> items = body.items() == null
                ? List.of()
                : body.items().stream()
                        .map(item -> new CheckInItem(
                                item.usuarioId(), item.nombre(), item.estado(), item.zonaId(), item.registradoEn()))
                        .toList();
        return new CheckInPage(body.page(), body.size(), body.total(), items);
    }

    /** Headers of an internal (service to service) call. */
    private void addServiceHeaders(HttpHeaders headers) {
        headers.set(IdentityHeaders.SERVICE_KEY, serviceKey);
        headers.set(IdentityHeaders.SERVICE_NAME, SERVICE_NAME);
        headers.set(CorrelationIds.HEADER, CorrelationIds.current());
    }

    /** Headers of a call to a user route: the service headers plus the system user identity. */
    private void addUserHeaders(HttpHeaders headers) {
        addServiceHeaders(headers);
        headers.set(IdentityHeaders.USER_ID, systemUserId);
        headers.set(IdentityHeaders.USER_ROLE, systemUserRole);
    }

    private void requireSystemUser() {
        try {
            UUID.fromString(systemUserId);
        } catch (IllegalArgumentException ex) {
            throw new ExternalServiceUnavailableException(SERVICE, new IllegalStateException(
                    "The check-in numbers need a system user: configure GATEWAY_SYSTEM_USER_ID"));
        }
    }

    private StateBody toBody(EventStatePayload payload) {
        // MS04 rejects a start date on a draft, so a draft never sends it.
        Instant startDate = payload.status() == EventStatus.DRAFT ? null : payload.startDate();
        return new StateBody(
                payload.eventId(),
                payload.name(),
                payload.simulacro(),
                StatusLabels.of(payload.status()),
                startDate,
                new Stage(ids(payload, ScopeType.BUILDING), ids(payload, ScopeType.ZONE)),
                payload.invitedRoles().stream().map(Role::name).sorted().toList());
    }

    private List<String> ids(EventStatePayload payload, ScopeType type) {
        return payload.scope().stream().filter(ref -> ref.type() == type).map(ScopeRef::id).toList();
    }

    record Stage(List<String> edificios, List<String> zonas) {
    }

    record StateBody(
            @JsonProperty("eventoId") UUID eventoId,
            @JsonProperty("nombre") String nombre,
            @JsonProperty("simulacro") boolean simulacro,
            @JsonProperty("estado") String estado,
            @JsonProperty("fechaInicio") Instant fechaInicio,
            @JsonProperty("escenario") Stage escenario,
            @JsonProperty("rolesConvocados") List<String> rolesConvocados) {
    }

    /** The part of the MS04 consolidated answer that MS06 uses. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConsolidatedBody(
            @JsonProperty("total_reportados") long totalReportados,
            @JsonProperty("totales_por_estado") Map<String, Long> totalesPorEstado,
            @JsonProperty("calculado_en") Instant calculadoEn) {
    }

    record ItemBody(String usuarioId, String nombre, String estado, String zonaId, Instant registradoEn) {
    }

    record PageBody(int page, int size, long total, List<ItemBody> items) {
    }
}
