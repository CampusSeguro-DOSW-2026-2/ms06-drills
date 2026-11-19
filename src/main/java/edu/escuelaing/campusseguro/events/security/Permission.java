package edu.escuelaing.campusseguro.events.security;

import java.util.List;

/**
 * Permission codes of MS06 (MS01 permission matrix). A code is {@code <kind>:<action>} where the kind
 * is {@code ms06:drill} for events with the SIMULACRO type and {@code ms06:real_event} for the rest.
 *
 * <p>MS01 decides which user has which permission; the API Gateway sends them in the
 * {@code X-User-Permission} header. MS06 only reads them, it never maps roles to permissions.
 */
public final class Permission {

    private static final String DRILL_PREFIX = "ms06:drill:";
    private static final String REAL_EVENT_PREFIX = "ms06:real_event:";

    /** What a permission allows to do. The suffix of the code is the lower-case name. */
    public enum Action {
        MANAGE, EXECUTE, EVALUATE, READ, COMPARE_REPORT;

        /** Suffix used in the permission code, for example {@code compare_report}. */
        public String suffix() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Permission() {
    }

    /**
     * Code of the permission for one kind of event and one action.
     *
     * @param simulacro {@code true} for a drill, {@code false} for a real event
     */
    public static String code(boolean simulacro, Action action) {
        return (simulacro ? DRILL_PREFIX : REAL_EVENT_PREFIX) + action.suffix();
    }

    /** Both codes (drill and real event) of an action, for rules that accept either kind. */
    public static List<String> anyKind(Action action) {
        return List.of(code(true, action), code(false, action));
    }
}
