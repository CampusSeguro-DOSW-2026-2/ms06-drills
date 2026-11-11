package edu.escuelaing.campusseguro.events.client;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Correlation id of the current request. The Gateway assigns the header and forwards it; MS06
 * repeats the same value when it calls another service. Outside a request (scheduled jobs) a
 * new value is generated.
 */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";

    /** Request attribute where the filter keeps the value, so it is still there in error dispatches. */
    public static final String ATTRIBUTE = CorrelationIds.class.getName() + ".value";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationIds() {
    }

    /** Value for the current request, or a new one when there is no request. */
    public static String current() {
        String value = CURRENT.get();
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    /**
     * Correlation id of a request: the value the filter kept, the received header, or the one of the
     * current thread. Error answers use it so they repeat the id the Gateway sent.
     */
    public static String of(HttpServletRequest request) {
        if (request != null) {
            Object kept = request.getAttribute(ATTRIBUTE);
            if (kept instanceof String value && !value.isBlank()) {
                return value;
            }
            String header = request.getHeader(HEADER);
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        return current();
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
