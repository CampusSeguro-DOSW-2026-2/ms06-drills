package edu.escuelaing.campusseguro.events.client.brigade;

import edu.escuelaing.campusseguro.events.client.CorrelationIds;
import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.ExternalCalls;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.StatusLabels;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.IdentityHeaders;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link BrigadeGateway}. Every call goes through the API Gateway with the MS06 service key
 * ({@code X-Service-Key}), never directly to MS05.
 *
 * <p>MS05 only accepts these internal routes for the role {@code SERVICE_MS06}, and its filter also
 * needs a user id, so each call sends {@code X-User-Role: SERVICE_MS06} and a technical user id
 * (the configured system user, or {@code ms06-service} when none is set).
 */
@Component
@Profile("!local & !test")
public class RestBrigadeGateway implements BrigadeGateway {

    private static final String SERVICE = "MS05";
    private static final String SERVICE_NAME = "MS06";
    private static final String FALLBACK_USER_ID = "ms06-service";

    private final RestClient client;
    private final String serviceKey;
    private final String userId;
    private final String serviceRole;
    private final String statePath;
    private final String brigadiersPath;
    private final String indicatorsPath;

    public RestBrigadeGateway(
            RestClientFactory factory,
            @Value("${app.gateway.url:http://localhost:8080}") String baseUrl,
            @Value("${app.gateway.service-key:}") String serviceKey,
            @Value("${app.gateway.system-user-id:}") String systemUserId,
            @Value("${app.gateway.ms05-service-role:SERVICE_MS06}") String serviceRole,
            @Value("${app.clients.ms05.state-path:/api/v1/bei/eventos/{eventId}/estado}") String statePath,
            @Value("${app.clients.ms05.brigadiers-path:/api/v1/bei/eventos/{eventId}/brigadistas}")
                    String brigadiersPath,
            @Value("${app.clients.ms05.indicators-path:/api/v1/bei/eventos/{eventId}/indicadores}")
                    String indicatorsPath) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException(
                    "app.gateway.service-key is required (GATEWAY_SERVICE_KEY)");
        }
        this.client = factory.createGateway(baseUrl);
        this.serviceKey = serviceKey.strip();
        this.userId = systemUserId == null || systemUserId.isBlank() ? FALLBACK_USER_ID : systemUserId.strip();
        this.serviceRole = serviceRole == null || serviceRole.isBlank() ? "SERVICE_MS06" : serviceRole.strip();
        this.statePath = statePath;
        this.brigadiersPath = brigadiersPath;
        this.indicatorsPath = indicatorsPath;
    }

    private void addHeaders(HttpHeaders headers) {
        headers.set(IdentityHeaders.SERVICE_KEY, serviceKey);
        headers.set(IdentityHeaders.SERVICE_NAME, SERVICE_NAME);
        headers.set(CorrelationIds.HEADER, CorrelationIds.current());
        headers.set(IdentityHeaders.USER_ID, userId);
        headers.set(IdentityHeaders.USER_ROLE, serviceRole);
    }

    @Override
    public void pushState(EventStatePayload payload) {
        ExternalCalls.run(SERVICE, () -> client.put()
                .uri(statePath, payload.eventId())
                .headers(this::addHeaders)
                .body(toBody(payload))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public BrigadierSnapshot brigadiers(UUID eventId) {
        BrigadiersBody body = ExternalCalls.call(SERVICE, () -> client.get()
                .uri(brigadiersPath, eventId)
                .headers(this::addHeaders)
                .retrieve()
                .body(BrigadiersBody.class));
        if (body == null) {
            throw new ExternalServiceUnavailableException(SERVICE, new IllegalStateException("Empty answer"));
        }
        List<BrigadierInfo> list = body.brigadistas() == null
                ? List.of()
                : body.brigadistas().stream()
                        .map(b -> new BrigadierInfo(
                                b.brigadistaId(), b.nombre(), b.zonaId(), "ACTIVO".equalsIgnoreCase(b.estado())))
                        .toList();
        return new BrigadierSnapshot(eventId, body.actualizadoEn(), list);
    }

    @Override
    public IncidentIndicators indicators(UUID eventId) {
        IndicatorsBody body = ExternalCalls.call(SERVICE, () -> client.get()
                .uri(indicatorsPath, eventId)
                .headers(this::addHeaders)
                .retrieve()
                .body(IndicatorsBody.class));
        if (body == null) {
            throw new ExternalServiceUnavailableException(SERVICE, new IllegalStateException("Empty answer"));
        }
        List<BrigadierPerformance> perBrigadier = body.porBrigadista() == null
                ? List.of()
                : body.porBrigadista().stream()
                        .map(p -> new BrigadierPerformance(p.brigadistaId(), p.casosAtendidos(), p.casosResueltos()))
                        .toList();
        return new IncidentIndicators(
                eventId,
                body.actualizadoEn(),
                body.totalIncidentes(),
                body.totalCasosAtendidos(),
                body.totalCasosResueltos(),
                body.tiempoPromedioAtencionSegundos(),
                perBrigadier);
    }

    private StateBody toBody(EventStatePayload payload) {
        List<ZoneBody> zones = payload.scope().stream()
                .filter(ref -> ref.type() == ScopeType.ZONE)
                .map(ref -> new ZoneBody(ref.id()))
                .toList();
        List<String> buildings = payload.scope().stream()
                .filter(ref -> ref.type() == ScopeType.BUILDING)
                .map(ScopeRef::id)
                .toList();
        return new StateBody(
                payload.eventId(),
                payload.name(),
                payload.simulacro() ? "SIMULACRO" : "REAL",
                payload.simulacro(),
                StatusLabels.of(payload.status()),
                payload.startDate(),
                payload.endDate(),
                zones,
                buildings);
    }

    record ZoneBody(String zonaId) {
    }

    record StateBody(
            UUID eventoId,
            String nombre,
            String tipoEvento,
            boolean simulacro,
            String estado,
            Instant fechaInicio,
            Instant fechaFin,
            List<ZoneBody> zonas,
            List<String> edificios) {
    }

    record BrigadierBody(String brigadistaId, String nombre, String zonaId, String estado) {
    }

    record BrigadiersBody(Instant actualizadoEn, List<BrigadierBody> brigadistas) {
    }

    record PerBrigadierBody(String brigadistaId, int casosAtendidos, int casosResueltos) {
    }

    record IndicatorsBody(
            Instant actualizadoEn,
            int totalIncidentes,
            int totalCasosAtendidos,
            int totalCasosResueltos,
            Long tiempoPromedioAtencionSegundos,
            List<PerBrigadierBody> porBrigadista) {
    }
}
