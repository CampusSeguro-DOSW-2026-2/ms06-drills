package edu.escuelaing.campusseguro.events.client.identity;

import edu.escuelaing.campusseguro.events.security.Role;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local and test version of {@link IdentityGateway}: accepts every role. */
@Component
@Profile({"local", "test"})
public class StubIdentityGateway implements IdentityGateway {

    @Override
    public void validateRoles(Set<Role> roles) {
        // Every role is valid in the stub.
    }
}
