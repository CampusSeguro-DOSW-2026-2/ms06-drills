package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.escuelaing.campusseguro.events.security.Permission.Action;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests that the permission codes match the MS01 matrix. */
class PermissionTest {

    @Test
    void buildsTheCodesOfBothKindsOfEvent() {
        assertEquals("ms06:drill:manage", Permission.code(true, Action.MANAGE));
        assertEquals("ms06:real_event:execute", Permission.code(false, Action.EXECUTE));
        assertEquals("ms06:drill:evaluate", Permission.code(true, Action.EVALUATE));
        assertEquals("ms06:real_event:read", Permission.code(false, Action.READ));
        assertEquals("ms06:drill:compare_report", Permission.code(true, Action.COMPARE_REPORT));
    }

    @Test
    void anyKindGivesTheDrillAndTheRealEventCode() {
        assertEquals(List.of("ms06:drill:read", "ms06:real_event:read"), Permission.anyKind(Action.READ));
    }
}
