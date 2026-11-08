package edu.escuelaing.campusseguro.events.client.campus;

import edu.escuelaing.campusseguro.events.client.ExternalCalls;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.ValidationCalls;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link CampusGateway}. Scope validation uses the endpoint MS03 already uses with MS02
 * ({@code POST /api/v1/catalogo/validar-alcance}). The routes endpoint is a proposal and its
 * path can be changed with a property until MS02 defines it.
 */
@Component
@Profile("!local & !test")
public class RestCampusGateway implements CampusGateway {

    private static final String SERVICE = "MS02";
    private static final String FIELD = "scope";

    private final RestClient client;
    private final String validateScopePath;
    private final String routesPath;

    public RestCampusGateway(
            RestClientFactory factory,
            @Value("${app.clients.ms02-url:http://localhost:8082}") String baseUrl,
            @Value("${app.clients.ms02.validate-scope-path:/api/v1/catalogo/validar-alcance}")
                    String validateScopePath,
            @Value("${app.clients.ms02.routes-path:/api/v1/catalogo/rutas}") String routesPath) {
        this.client = factory.create(baseUrl);
        this.validateScopePath = validateScopePath;
        this.routesPath = routesPath;
    }

    @Override
    public void validateScope(ScopeType type, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<String> invalid = ValidationCalls.invalidIds(
                client, SERVICE, validateScopePath, new ScopeValidationRequest(type.name(), List.copyOf(ids)));
        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(FIELD, invalid);
        }
    }

    @Override
    public List<ScopeRoute> routes(Collection<ScopeRef> scope) {
        if (scope == null || scope.isEmpty()) {
            return List.of();
        }
        List<ScopeItem> items = scope.stream().map(ref -> new ScopeItem(ref.type().name(), ref.id())).toList();
        List<RouteItem> routes = ExternalCalls.call(SERVICE, () -> client.post()
                .uri(routesPath)
                .body(new RoutesRequest(items))
                .retrieve()
                .body(new ParameterizedTypeReference<List<RouteItem>>() {
                }));
        if (routes == null) {
            return List.of();
        }
        return routes.stream()
                .map(route -> new ScopeRoute(ScopeType.valueOf(route.tipo()), route.id(), route.nombre(), route.url()))
                .toList();
    }

    record ScopeValidationRequest(String tipo, List<String> ids) {
    }

    record ScopeItem(String tipo, String id) {
    }

    record RoutesRequest(List<ScopeItem> alcance) {
    }

    record RouteItem(String tipo, String id, String nombre, String url) {
    }
}
