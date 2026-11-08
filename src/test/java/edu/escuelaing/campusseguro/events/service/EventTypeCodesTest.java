package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.exception.InvalidFieldException;
import org.junit.jupiter.api.Test;

/** Tests the generation of the code of an event type from its name. */
class EventTypeCodesTest {

    @Test
    void removesAccentsAndUsesUnderscores() {
        assertEquals("FUGA_DE_GAS", EventTypeCodes.fromName("Fuga de gas"));
        assertEquals("INUNDACION", EventTypeCodes.fromName("Inundación"));
        assertEquals("SISMO_NOCTURNO", EventTypeCodes.fromName("  sismo -- nocturno! "));
    }

    @Test
    void keepsDigits() {
        assertEquals("ALERTA_2", EventTypeCodes.fromName("Alerta 2"));
    }

    @Test
    void isCutToFortyCharactersWithoutTrailingUnderscore() {
        String code = EventTypeCodes.fromName("Evento de evacuacion masiva por emergencia quimica grave");
        assertTrue(code.length() <= 40);
        assertTrue(!code.endsWith("_"));
    }

    @Test
    void rejectsNamesWithoutLettersOrDigits() {
        assertThrows(InvalidFieldException.class, () -> EventTypeCodes.fromName("!!! ???"));
    }
}
