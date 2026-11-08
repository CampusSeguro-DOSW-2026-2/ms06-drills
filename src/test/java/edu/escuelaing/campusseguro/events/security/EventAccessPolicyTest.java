package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Tests who may manage which kind of event. */
class EventAccessPolicyTest {

    private final EventAccessPolicy policy = new EventAccessPolicy();
    private final AccessActor coordinator = new AccessActor("c", Set.of(Role.COORDINADOR));
    private final AccessActor organizer = new AccessActor("o", Set.of(Role.ORGANIZADOR));
    private final AccessActor admin = new AccessActor("a", Set.of(Role.ADMIN));

    @Test
    void theCoordinatorManagesRealEventsAndTheOrganizerManagesDrills() {
        assertTrue(policy.canManage(coordinator, false));
        assertFalse(policy.canManage(coordinator, true));
        assertTrue(policy.canManage(organizer, true));
        assertFalse(policy.canManage(organizer, false));
    }

    @Test
    void theAdminCannotManageEvents() {
        assertFalse(policy.canManage(admin, false));
        assertFalse(policy.canManage(admin, true));
    }

    @Test
    void assertCanManageAcceptsTheRightRole() {
        assertDoesNotThrow(() -> policy.assertCanManage(coordinator, false, "create"));
        assertDoesNotThrow(() -> policy.assertCanManage(organizer, true, "edit"));
    }

    @Test
    void assertCanManageNamesTheRoleAndTheActionWhenDenied() {
        AccessDeniedException real = assertThrows(AccessDeniedException.class,
                () -> policy.assertCanManage(organizer, false, "create"));
        assertTrue(real.getMessage().contains("COORDINADOR"));
        assertTrue(real.getMessage().contains("create"));

        AccessDeniedException drill = assertThrows(AccessDeniedException.class,
                () -> policy.assertCanManage(coordinator, true, "edit"));
        assertTrue(drill.getMessage().contains("ORGANIZADOR"));
        assertTrue(drill.getMessage().contains("edit"));
    }
}
