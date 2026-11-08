package edu.escuelaing.campusseguro.events.client.checkin;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.ExternalCalls;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.StatusLabels;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Real {@link CheckInGateway}. The paths are proposals and can be changed with properties. */
@Component
@Profile("!local & !test")
public class RestCheckInGateway implements CheckInGateway {

    private static final String SERVICE = "MS04";

    private final RestClient client;
    private final String statePath;
    private final String indicatorsPath;
    private final String checkinsPath;

    public RestCheckInGateway(
            RestClientFactory factory,
            @Value("${app.clients.ms04-url:http://localhost:8084}") String baseUrl,
            @Value("${app.clients.ms04.state-path:/api/v1/chi/eventos/{eventId}/estado}") String statePath,
            @Value("${app.clients.ms04.indicators-path:/api/v1/chi/eventos/{eventId}/indicadores}")
                    String indicatorsPath,
            @Value("${app.clients.ms04.checkins-path:/api/v1/chi/eventos/{eventId}/checkins}")
                    String checkinsPath) {
        this.client = factory.create(baseUrl);
        this.statePath = statePath;
        this.indicatorsPath = indicatorsPath;
        this.checkinsPath = checkinsPath;
    }

    @Override
    public void pushState(EventStatePayload payload) {
        ExternalCalls.run(SERVICE, () -> client.put()
                .uri(statePath, payload.eventId())
                .body(toBody(payload))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public CheckInIndicators indicators(UUID eventId) {
        IndicatorsBody body = ExternalCalls.call(SERVICE, () -> client.get()
                .uri(indicatorsPath, eventId)
                .retrieve()
                .body(IndicatorsBody.class));
        if (body == null) {
            throw new ExternalServiceUnavailableException(SERVICE, new IllegalStateException("Empty answer"));
        }
        return new CheckInIndicators(
                eventId,
                body.actualizadoEn(),
                body.totalCheckins(),
                body.porEstado() == null ? Map.of() : body.porEstado(),
                body.porcentajeParticipacion());
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

    private StateBody toBody(EventStatePayload payload) {
        return new StateBody(
                payload.eventId(),
                payload.name(),
                payload.simulacro(),
                StatusLabels.of(payload.status()),
                payload.startDate(),
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

    record IndicatorsBody(
            Instant actualizadoEn,
            int totalCheckins,
            Map<String, Integer> porEstado,
            BigDecimal porcentajeParticipacion) {
    }

    record ItemBody(String usuarioId, String nombre, String estado, String zonaId, Instant registradoEn) {
    }

    record PageBody(int page, int size, long total, List<ItemBody> items) {
    }
}
