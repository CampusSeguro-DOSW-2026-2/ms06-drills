package edu.escuelaing.campusseguro.events.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Real-time monitoring of an event that is In progress.
 *
 * @param checkIn            check-in numbers (CHI); {@code null} if CHI does not answer
 * @param incidents          incident numbers (BEI); {@code null} if BEI does not answer
 * @param brigadiersByZone   brigadiers grouped by zone (last copy saved from BEI)
 * @param brigadiersSyncedAt when the brigadier copy was last synced with BEI; {@code null} if never
 * @param messages           what is missing or empty and why
 */
public record EventMonitoringResponse(
        CheckInProgress checkIn,
        IncidentProgress incidents,
        List<ZoneBrigadiers> brigadiersByZone,
        Instant brigadiersSyncedAt,
        List<EventWarning> messages) {

    /**
     * Check-in progress.
     *
     * @param updatedAt        when CHI calculated the numbers
     * @param totalCheckins    check-ins registered
     * @param byStatus         check-ins per status
     * @param participationPct percentage of invited people who did the check-in
     */
    public record CheckInProgress(
            Instant updatedAt, int totalCheckins, Map<String, Integer> byStatus, BigDecimal participationPct) {
    }

    /**
     * Incident progress.
     *
     * @param updatedAt           when BEI calculated the numbers
     * @param totalIncidents      incidents reported
     * @param attended            cases attended
     * @param resolved            cases resolved
     * @param pending             incidents not attended yet
     * @param avgAttentionSeconds average attention time; {@code null} if BEI does not send it
     */
    public record IncidentProgress(
            Instant updatedAt, int totalIncidents, int attended, int resolved, int pending,
            Long avgAttentionSeconds) {
    }

    /**
     * Brigadiers of one zone.
     *
     * @param zoneId      zone ({@code null} when the brigadier has no zone yet)
     * @param activeCount brigadiers that are active in the zone
     * @param brigadiers  all brigadiers of the zone
     */
    public record ZoneBrigadiers(String zoneId, long activeCount, List<Brigadier> brigadiers) {
    }

    /** A brigadier of a zone. */
    public record Brigadier(String brigadierId, String name, boolean active) {
    }
}
