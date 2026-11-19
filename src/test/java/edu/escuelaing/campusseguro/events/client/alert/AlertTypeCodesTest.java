package edu.escuelaing.campusseguro.events.client.alert;

import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests the MS06 codes that are sent to MS03 as alert types. */
class AlertTypeCodesTest {

    private EventTypeEntity type(String code, boolean active, boolean alertEligible) {
        EventTypeEntity entity = new EventTypeEntity();
        entity.setCode(code);
        entity.setName(code);
        entity.setActive(active);
        entity.setAlertEligible(alertEligible);
        return entity;
    }

    @Test
    void anEventWithoutTypesSendsOtro() {
        assertEquals(List.of("OTRO"), AlertTypeCodes.from(Set.of()));
        assertEquals(List.of("OTRO"), AlertTypeCodes.from(null));
    }

    @Test
    void simulacroIsNeverAnAlertType() {
        EventTypeEntity simulacro = type(EventTypeEntity.SIMULACRO_CODE, true, true);

        assertEquals(List.of("OTRO"), AlertTypeCodes.from(Set.of(simulacro)));
        assertEquals(List.of("SISMO"), AlertTypeCodes.from(Set.of(simulacro, type("SISMO", true, true))));
    }

    @Test
    void sendsEveryEligibleActiveCodeSortedAndWithoutDuplicates() {
        List<String> codes = AlertTypeCodes.from(Set.of(
                type("SISMO", true, true),
                type("INCENDIO", true, true),
                type("INUNDACION", true, true),
                type("SISMO", true, true)));

        assertEquals(List.of("INCENDIO", "INUNDACION", "SISMO"), codes);
    }

    @Test
    void inactiveTypesAreNotSent() {
        List<String> codes = AlertTypeCodes.from(Set.of(
                type("SISMO", false, true),
                type("INCENDIO", true, true)));

        assertEquals(List.of("INCENDIO"), codes);
    }

    @Test
    void typesThatAreNotAlertEligibleAreNotSent() {
        List<String> codes = AlertTypeCodes.from(Set.of(
                type("SISMO", true, false),
                type("INCENDIO", true, true)));

        assertEquals(List.of("INCENDIO"), codes);
    }

    @Test
    void onlyNonEligibleTypesFallBackToOtro() {
        List<String> codes = AlertTypeCodes.from(Set.of(
                type("SISMO", false, true),
                type("INCENDIO", true, false)));

        assertEquals(List.of("OTRO"), codes);
    }
}
