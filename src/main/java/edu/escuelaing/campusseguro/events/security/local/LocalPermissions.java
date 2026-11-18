package edu.escuelaing.campusseguro.events.security.local;

import edu.escuelaing.campusseguro.events.security.Permission;
import edu.escuelaing.campusseguro.events.security.Permission.Action;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Local testing only: the MS06 permissions that MS01 gives to each role in its initial matrix, so
 * the local tokens carry the same permissions the Gateway would send. In production MS06 never
 * uses this class; it reads the {@code X-User-Permission} header.
 */
public final class LocalPermissions {

    private LocalPermissions() {
    }

    /**
     * MS06 permissions of a role: the organizer has all the drill permissions, the coordinator all
     * the real event ones, the auditor only read and compare_report of both kinds. Other roles have none.
     */
    public static Set<String> of(Role role) {
        Set<String> codes = new LinkedHashSet<>();
        switch (role) {
            case ORGANIZADOR -> Arrays.stream(Action.values()).forEach(a -> codes.add(Permission.code(true, a)));
            case COORDINADOR -> Arrays.stream(Action.values()).forEach(a -> codes.add(Permission.code(false, a)));
            case AUDITOR -> {
                for (Action action : new Action[] {Action.READ, Action.COMPARE_REPORT}) {
                    codes.addAll(Permission.anyKind(action));
                }
            }
            default -> {
                // No MS06 permissions.
            }
        }
        return codes;
    }
}
