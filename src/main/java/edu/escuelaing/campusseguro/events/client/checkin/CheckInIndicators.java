package edu.escuelaing.campusseguro.events.client.checkin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Check-in numbers of one event, as CHI reports them.
 *
 * @param eventId          event
 * @param updatedAt        when CHI calculated them
 * @param totalCheckins    check-ins registered
 * @param byStatus         check-ins per status (status names are CHI's)
 * @param participationPct percentage of invited people who did the check-in
 */
public record CheckInIndicators(
        UUID eventId,
        Instant updatedAt,
        int totalCheckins,
        Map<String, Integer> byStatus,
        BigDecimal participationPct) {
}
