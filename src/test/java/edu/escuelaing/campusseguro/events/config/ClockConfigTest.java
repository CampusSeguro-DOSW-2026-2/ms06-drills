/**
 * Class in which we test the clock performance, given the selected zone (America/Bogota)
 */
package edu.escuelaing.campusseguro.events.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    private final ClockConfig config = new ClockConfig();
    
    /**
     * Method that verifies that the clack is using the configured TimeZone
     * @param void
     */
    @Test
    void usesTheConfiguredTimezone() {
        Clock clock = config.clock("America/Bogota");

        assertEquals(ZoneId.of("America/Bogota"), clock.getZone());
    }

    /**
     * Method that verifies that if the configured TimeZone changes
     * the clock performance will change
     * @param void
     */
    @Test
    void usesAnotherTimezoneWhenConfigured() {
        Clock clock = config.clock("UTC");

        assertEquals(ZoneId.of("UTC"), clock.getZone());
    }

    /**
     * Method that verifies the clock don´t admit an invalid TimeZone
     * @param void
     */
    @Test
    void rejectsAnInvalidTimezone() {
        assertThrows(DateTimeException.class, () -> config.clock("No/Existe"));
    }
}
