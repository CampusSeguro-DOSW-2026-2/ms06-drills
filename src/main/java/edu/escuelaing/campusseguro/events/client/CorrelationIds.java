package edu.escuelaing.campusseguro.events.client;

import java.util.UUID;

/**
 * Correlation id of the current request. The Gateway assigns the header and forwards it; MS06
 * repeats the same value when it calls another service. Outside a request (scheduled jobs) a
 * new value is generated.
 */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationIds() {
    }

    /** Value for the current request, or a new one when there is no request. */
    public static String current() {
        String value = CURRENT.get();
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    public static void set(String value) {
        if (value == null || value.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(value);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
