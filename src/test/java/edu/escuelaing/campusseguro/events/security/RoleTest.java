package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void hasTheEightSystemRoles() {
        assertEquals(8, Role.values().length);
    }

    @Test
    void authorityHasTheRolePrefix() {
        assertEquals("ROLE_COORDINADOR", Role.COORDINADOR.authority());
    }

    @Test
    void communityIsTheSixCampusRolesOnly() {
        EnumSet<Role> community = EnumSet.of(
                Role.ESTUDIANTE, Role.PROFESOR, Role.ADMINISTRATIVO,
                Role.BRIGADISTA, Role.COORDINADOR, Role.ORGANIZADOR);

        for (Role role : Role.values()) {
            assertEquals(community.contains(role), role.isCommunity(), role.name());
        }
        assertFalse(Role.ADMIN.isCommunity());
        assertFalse(Role.AUDITOR.isCommunity());
    }

    @Test
    void parsesFullNamesIgnoringCaseAndPrefix() {
        assertEquals(Optional.of(Role.COORDINADOR), Role.fromClaim("COORDINADOR"));
        assertEquals(Optional.of(Role.COORDINADOR), Role.fromClaim("coordinador"));
        assertEquals(Optional.of(Role.COORDINADOR), Role.fromClaim("ROLE_COORDINADOR"));
        assertEquals(Optional.of(Role.AUDITOR), Role.fromClaim("  role_auditor "));
    }

    @Test
    void parsesTheAbbreviations() {
        assertEquals(Optional.of(Role.ESTUDIANTE), Role.fromClaim("EST"));
        assertEquals(Optional.of(Role.PROFESOR), Role.fromClaim("PROF"));
        assertEquals(Optional.of(Role.ADMINISTRATIVO), Role.fromClaim("ADMTV"));
        assertEquals(Optional.of(Role.BRIGADISTA), Role.fromClaim("BRIG"));
        assertEquals(Optional.of(Role.COORDINADOR), Role.fromClaim("COORD"));
        assertEquals(Optional.of(Role.ORGANIZADOR), Role.fromClaim("ORG"));
    }

    @Test
    void adminAndAuditorOnlyMatchTheirFullName() {
        assertEquals(Optional.of(Role.ADMIN), Role.fromClaim("ADMIN"));
        assertTrue(Role.fromClaim("ADM").isEmpty());
    }

    @Test
    void ignoresUnknownBlankAndNullValues() {
        assertTrue(Role.fromClaim("DESCONOCIDO").isEmpty());
        assertTrue(Role.fromClaim("").isEmpty());
        assertTrue(Role.fromClaim("   ").isEmpty());
        assertTrue(Role.fromClaim(null).isEmpty());
    }
}
