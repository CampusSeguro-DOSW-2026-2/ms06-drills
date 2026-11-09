package edu.escuelaing.campusseguro.events.client.alert;

import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps the event types of MS06 to the alert types of MS03.
 *
 * <p>MS03 only understands {@code EARTHQUAKE}, {@code FIRE}, {@code OTHER} and
 * {@code OPERATIVA}. An event may have several types; the most specific one is
 * chosen and the rest falls back to {@code OTHER}.
 */
public final class AlertTypes {

    private AlertTypes() {
    }

    public static List<String> from(Set<EventTypeEntity> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return List.of("OTHER");
        }
        if (anyMatches(eventTypes, "SISMO", "TERREMOTO", "EARTHQUAKE")) {
            return List.of("EARTHQUAKE");
        }
        if (anyMatches(eventTypes, "INCENDIO", "FIRE")) {
            return List.of("FIRE");
        }
        return List.of("OTHER");
    }

    private static boolean anyMatches(Set<EventTypeEntity> eventTypes, String... tokens) {
        return eventTypes.stream().anyMatch(type -> matches(type, tokens));
    }

    private static boolean matches(EventTypeEntity type, String... tokens) {
        String text = normalize(type.getCode()) + " " + normalize(type.getName());
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT);
    }
}
