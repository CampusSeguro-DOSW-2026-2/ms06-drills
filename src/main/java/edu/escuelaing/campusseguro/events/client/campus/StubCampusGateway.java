package edu.escuelaing.campusseguro.events.client.campus;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local and test version of {@link CampusGateway}: every id is valid and every route is a fake link. */
@Component
@Profile({"local", "test"})
public class StubCampusGateway implements CampusGateway {

    @Override
    public void validateScope(ScopeType type, Collection<String> ids) {
        // Every building and zone is valid in the stub.
    }

    @Override
    public List<ScopeRoute> routes(Collection<ScopeRef> scope) {
        return scope.stream()
                .map(ref -> new ScopeRoute(ref.type(), ref.id(), ref.id(), "stub://routes/" + ref.id()))
                .toList();
    }
}
