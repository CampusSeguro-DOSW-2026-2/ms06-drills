package edu.escuelaing.campusseguro.events.client.brigade;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.ExternalCalls;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.StatusLabels;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Real {@link BrigadeGateway}. The paths are proposals and can be changed with properties. */
@Component
@Profile("!local & !test")
public class RestBrigadeGateway implements BrigadeGateway {

    private static final String SERVICE = "MS05";

    private final RestClient client;
    private final String statePath;
    private final String brigadiersPath;
    private final String indicatorsPath;

    public RestBrigadeGateway(
            RestClientFactory factory,
            @Value("${app.clients.ms05-url:http://localhost:8085}") String baseUrl,
            @Value("${app.clients.ms05.state-path:/api/v1/bei/eventos/{eventId}/estado}") String statePath,
            @Value("${app.clients.ms05.brigadiers-path:/api/v1/bei/eventos/{eventId}/brigadistas}")
                    String brigadiersPath,
            @Value("${app.clients.ms05.indicators-path:/api/v1/bei/eventos/{eventId}/indicadores}")
                    String indicatorsPath) {
        this.client = factory.create(baseUrl);
        this.statePath = statePath;
        this.brigadiersPath = brigadiersPath;
        this.indicatorsPath = indicatorsPath;
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
    public BrigadierSnapshot brigadiers(UUID eventId) {
        BrigadiersBody body = ExternalCalls.call(SERVICE, () -> client.get()
                .uri(brigadiersPath, eventId)
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
