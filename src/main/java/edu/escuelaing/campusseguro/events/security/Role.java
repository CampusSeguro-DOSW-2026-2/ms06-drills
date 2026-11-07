package edu.escuelaing.campusseguro.events.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Roles del sistema (los emite MS01 en el claim de roles del JWT).
 * COM (comunidad en el campus) agrupa a ESTUDIANTE, PROFESOR, ADMINISTRATIVO,
 * BRIGADISTA, COORDINADOR y ORGANIZADOR. ADMIN y AUDITOR no hacen parte de COM.
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

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    public boolean isCommunity() {
        return COMMUNITY.contains(this);
    }

    /**
     * Interpreta el valor de un claim: acepta el nombre completo o la
     * abreviatura, con o sin el prefijo ROLE_ y sin importar mayúsculas.
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
