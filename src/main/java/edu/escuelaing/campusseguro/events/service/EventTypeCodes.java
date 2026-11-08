package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.exception.InvalidFieldException;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Builds the stable code of an event type from its name: no accents, uppercase, letters and digits
 * separated by underscores, at most 40 characters. Example: "Fuga de gas" becomes FUGA_DE_GAS.
 */
public final class EventTypeCodes {

    private static final int MAX_LENGTH = 40;

    private EventTypeCodes() {
    }

    /**
     * Generates the code of a name.
     *
     * @throws InvalidFieldException if the name has no letters or digits
     */
    public static String fromName(String name) {
        String noAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String code = noAccents.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        code = trimUnderscores(code);
        if (code.length() > MAX_LENGTH) {
            code = trimUnderscores(code.substring(0, MAX_LENGTH));
        }
        if (code.isEmpty()) {
            throw new InvalidFieldException("name", "The name must have letters or digits");
        }
        return code;
    }

    private static String trimUnderscores(String value) {
        return value.replaceAll("^_+|_+$", "");
    }
}
