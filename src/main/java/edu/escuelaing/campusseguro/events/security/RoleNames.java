package edu.escuelaing.campusseguro.events.security;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Names of the roles that an event invites.
 *
 * <p>MS01 keeps the roles as data: the administrator creates and renames them, and MS06 only stores the name of the
 * role. The name must keep the format of the standards ({@code ESTUDIANTE}, {@code ADMINISTRATIVO}), the same one the
 * API Gateway accepts.
 */
public final class RoleNames {

    /** Same format MS01 accepts for a stored role: upper case letter, then upper case, digits or underscore. */
    private static final Pattern STORED = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");

    /** Longest stored name; it is the size of {@code event_participants.role}. */
    public static final int MAX_LENGTH = 30;

    private RoleNames() {
    }

    /**
     * Tells if the value can be stored as the name of an invited role.
     *
     * @param value candidate name
     * @return true if it has the format of the standards
     */
    public static boolean hasStoredFormat(String value) {
        return value != null && STORED.matcher(value).matches();
    }

    /**
     * Upper-case name without surrounding blanks, the form used to store and compare roles.
     *
     * @param value raw name
     * @return normalized name, or an empty string when the value is null
     */
    public static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }
}
