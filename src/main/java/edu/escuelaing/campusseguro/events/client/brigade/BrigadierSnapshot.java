package edu.escuelaing.campusseguro.events.client.brigade;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Brigadiers of one event at a given moment.
 *
 * @param eventId    event
 * @param updatedAt  when BEI calculated the list
 * @param brigadiers brigadiers of the event
 */
public record BrigadierSnapshot(UUID eventId, Instant updatedAt, List<BrigadierInfo> brigadiers) {
}
