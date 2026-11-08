package edu.escuelaing.campusseguro.events.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * System roles (issued by MS01 in the roles claim of the JWT).
 * COM (campus community) groups ESTUDIANTE, PROFESOR, ADMINISTRATIVO,
 * BRIGADISTA, COORDINADOR and ORGANIZADOR. ADMIN and AUDITOR are not part of COM.
 */
public enum Role {
    ESTUDIANTE("EST"),
    PROFESOR("PROF"),
    ADMINISTRATIVO("ADMTV"),
    BRIGADISTA("BRIG"),
    COORDINADOR("COORD"),
    ORGANIZADOR("ORG"),
    ADMIN(null),
    AUDITOR(null);

    private static final String AUTHORITY_PREFIX = "ROLE_";

    private static final Set<Role> COMMUNITY = Collections.unmodifiableSet(
            EnumSet.of(
                    ESTUDIANTE,
                    PROFESOR,
                    ADMINISTRATIVO,
                    BRIGADISTA,
                    COORDINADOR,
                    ORGANIZADOR));

    private final String abbreviation;

    Role(String abbreviation) {
        this.abbreviation = abbreviation;
    }

    /** Spring Security authority name for this role, with the {@code ROLE_} prefix. */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /** Tells if the role is part of COM (campus community); ADMIN and AUDITOR are not. */
    public boolean isCommunity() {
        return COMMUNITY.contains(this);
    }

    /**
     * Reads a claim value: accepts the full name or the abbreviation, with or
     * without the ROLE_ prefix, in any letter case.
     */
    public static Optional<Role> fromClaim(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(AUTHORITY_PREFIX)) {
            normalized = normalized.substring(AUTHORITY_PREFIX.length());
        }
        for (Role role : values()) {
            if (role.name().equals(normalized) || normalized.equals(role.abbreviation)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
