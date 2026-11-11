package edu.escuelaing.campusseguro.events.client.campus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.escuelaing.campusseguro.events.client.CorrelationIds;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.security.IdentityHeaders;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real {@link CampusGateway}. Every call goes through the API Gateway with the MS06 service key;
 * nothing is sent directly to MS02.
 *
 * <p>MS02 has no batch validation, so each id is checked with the routes it already has:
 * {@code GET /api/v1/buildings/{id}} and {@code GET /api/v1/safe-zones/{id}} (the same way MS03
 * does it). A 404 or 400 means the id is not valid; any other failure means MS02 is unavailable.
 *
 * <p>MS02 authorizes by user identity, not by service key, so the calls carry the system user
 * ({@code X-User-Id} and {@code X-User-Role}). Every role has the campus read permission.
 *
 * <p>Evacuation routes exist per building ({@code GET /api/v1/buildings/{id}/evacuation-routes});
 * a zone is only a destination, so it has no routes of its own.
 */
@Component
@Profile("!local & !test")
public class RestCampusGateway implements CampusGateway {

    private static final String SERVICE = "MS02";
    private static final String SERVICE_NAME = "ms06";
    private static final String FIELD = "scope";

    private final RestClient client;
    private final String serviceKey;
    private final String systemUserId;
    private final String systemUserRole;
    private final String buildingPath;
    private final String zonePath;
    private final String routesPath;

    public RestCampusGateway(
            RestClientFactory factory,
            @Value("${app.gateway.url:http://localhost:8080}") String baseUrl,
            @Value("${app.gateway.service-key:}") String serviceKey,
            @Value("${app.gateway.system-user-id:}") String systemUserId,
            @Value("${app.gateway.system-user-role:ORGANIZADOR}") String systemUserRole,
            @Value("${app.clients.ms02.building-path:/api/v1/buildings/{id}}") String buildingPath,
            @Value("${app.clients.ms02.zone-path:/api/v1/safe-zones/{id}}") String zonePath,
            @Value("${app.clients.ms02.routes-path:/api/v1/buildings/{id}/evacuation-routes}")
                    String routesPath) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("app.gateway.service-key is required (GATEWAY_SERVICE_KEY)");
        }
        this.client = factory.createGateway(baseUrl);
        this.serviceKey = serviceKey.strip();
        this.systemUserId = systemUserId == null ? "" : systemUserId.strip();
        this.systemUserRole = systemUserRole == null || systemUserRole.isBlank()
                ? Role.ORGANIZADOR.name()
                : systemUserRole.strip();
        this.buildingPath = buildingPath;
        this.zonePath = zonePath;
        this.routesPath = routesPath;
    }

    @Override
    public void validateScope(ScopeType type, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        requireSystemUser();
        String path = type == ScopeType.BUILDING ? buildingPath : zonePath;
        List<String> invalid = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank() || !exists(path, id)) {
                invalid.add(id);
            }
        }
        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(FIELD, invalid);
        }
    }

    @Override
    public List<ScopeRoute> routes(Collection<ScopeRef> scope) {
        if (scope == null || scope.isEmpty()) {
            return List.of();
        }
        requireSystemUser();
        List<ScopeRoute> result = new ArrayList<>();
        for (ScopeRef ref : scope) {
            if (ref.type() == ScopeType.BUILDING) {
                result.addAll(buildingRoutes(ref.id()));
            }
        }
        return result;
    }

    /** Routes of one building; the ones that have no route defined are left out. */
    private List<ScopeRoute> buildingRoutes(String buildingId) {
        List<RouteBody> body;
        try {
            body = client.get()
                    .uri(routesPath, buildingId)
                    .headers(this::addHeaders)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<RouteBody>>() {
                    });
        } catch (RestClientResponseException ex) {
            // The building is not in CAM (or is inactive): it simply has no routes to show.
            int status = ex.getStatusCode().value();
            if (status == 400 || status == 404 || status == 422) {
                return List.of();
            }
            throw new ExternalServiceUnavailableException(SERVICE, ex);
        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException(SERVICE, ex);
        }
        if (body == null) {
            return List.of();
        }
        return body.stream()
                .filter(route -> route.tieneRuta())
                .map(route -> new ScopeRoute(ScopeType.BUILDING, buildingId, label(route), null))
                .toList();
    }

    private static String label(RouteBody route) {
        String origin = route.espacioNombre() == null ? "Space" : route.espacioNombre();
        return route.zonaSeguraNombre() == null ? origin : origin + " -> " + route.zonaSeguraNombre();
    }

    private boolean exists(String path, String id) {
        try {
            client.get()
                    .uri(path, id)
                    .headers(this::addHeaders)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            // 404: the id does not exist. 400: CAM cannot read the id (bad format).
            if (status == 400 || status == 404) {
                return false;
            }
            throw new ExternalServiceUnavailableException(SERVICE, ex);
        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException(SERVICE, ex);
        }
    }

    private void addHeaders(HttpHeaders headers) {
        headers.set(IdentityHeaders.SERVICE_KEY, serviceKey);
        headers.set(IdentityHeaders.SERVICE_NAME, SERVICE_NAME);
        headers.set(IdentityHeaders.USER_ID, systemUserId);
        headers.set(IdentityHeaders.USER_ROLE, systemUserRole);
        headers.set(CorrelationIds.HEADER, CorrelationIds.current());
    }

    private void requireSystemUser() {
        try {
            UUID.fromString(systemUserId);
        } catch (IllegalArgumentException ex) {
            throw new ExternalServiceUnavailableException(SERVICE, new IllegalStateException(
                    "CAM calls need a system user: configure GATEWAY_SYSTEM_USER_ID"));
        }
    }

    /** The part of the MS02 route answer that MS06 uses. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteBody(String espacioNombre, boolean tieneRuta, String zonaSeguraNombre) {
    }
}
