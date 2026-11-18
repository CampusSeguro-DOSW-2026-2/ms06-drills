package edu.escuelaing.campusseguro.events.security;

import edu.escuelaing.campusseguro.events.security.local.LocalPermissions;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Test helper: builds actors with the roles and the MS06 permissions MS01 gives to those roles. */
public final class TestActors {

    private TestActors() {
    }

    /** Actor with the given roles and the permissions of those roles (see {@link LocalPermissions}). */
    public static AccessActor actor(String userId, Role... roles) {
        Set<Role> roleSet = roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(Arrays.asList(roles));
        Set<String> permissions = new HashSet<>();
        roleSet.forEach(role -> permissions.addAll(LocalPermissions.of(role)));
        return new AccessActor(userId, Set.copyOf(roleSet), Set.copyOf(permissions));
    }
}
