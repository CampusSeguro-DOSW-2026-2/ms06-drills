package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.security.Permission.Action;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Tests who may do what on each kind of event, according to the permissions. */
class EventAccessPolicyTest {

    private final EventAccessPolicy policy = new EventAccessPolicy();
    private final AccessActor coordinator = TestActors.actor("c", Role.COORDINADOR);
    private final AccessActor organizer = TestActors.actor("o", Role.ORGANIZADOR);
    private final AccessActor auditor = TestActors.actor("u", Role.AUDITOR);
    private final AccessActor admin = TestActors.actor("a", Role.ADMIN);

    @Test
    void theCoordinatorManagesRealEventsAndTheOrganizerManagesDrills() {
        assertTrue(policy.canManage(coordinator, false));
        assertFalse(policy.canManage(coordinator, true));
        assertTrue(policy.canManage(organizer, true));
        assertFalse(policy.canManage(organizer, false));
    }

    @Test
    void theAdminAndTheAuditorCannotManageEvents() {
        assertFalse(policy.canManage(admin, false));
        assertFalse(policy.canManage(admin, true));
        assertFalse(policy.canManage(auditor, false));
        assertFalse(policy.canManage(auditor, true));
    }

    @Test
    void theAuditorReadsBothKindsButCannotExecute() {
        assertTrue(policy.can(auditor, true, Action.READ));
        assertTrue(policy.can(auditor, false, Action.READ));
        assertFalse(policy.can(auditor, true, Action.EXECUTE));
        assertFalse(policy.can(auditor, false, Action.EXECUTE));
    }

    @Test
    void theAdminHasNoMs06Permission() {
        assertFalse(policy.can(admin, true, Action.READ));
        assertFalse(policy.can(admin, false, Action.READ));
    }

    @Test
    void assertCanManageAcceptsTheRightPermission() {
        assertDoesNotThrow(() -> policy.assertCanManage(coordinator, false, "create"));
        assertDoesNotThrow(() -> policy.assertCanManage(organizer, true, "edit"));
    }

    @Test
    void assertCanManageNamesThePermissionAndTheActionWhenDenied() {
        AccessDeniedException real = assertThrows(AccessDeniedException.class,
                () -> policy.assertCanManage(organizer, false, "create"));
        assertTrue(real.getMessage().contains("ms06:real_event:manage"));
        assertTrue(real.getMessage().contains("create"));

        AccessDeniedException drill = assertThrows(AccessDeniedException.class,
                () -> policy.assertCanManage(coordinator, true, "edit"));
        assertTrue(drill.getMessage().contains("ms06:drill:manage"));
        assertTrue(drill.getMessage().contains("edit"));
    }

    @Test
    void assertCanChecksTheRequestedAction() {
        assertDoesNotThrow(() -> policy.assertCan(organizer, true, Action.EXECUTE, "finish"));
        assertThrows(AccessDeniedException.class,
                () -> policy.assertCan(auditor, true, Action.EXECUTE, "finish"));
    }
}
