package edu.escuelaing.campusseguro.events.client.brigade;

/**
 * A brigadier assigned to an event.
 *
 * @param brigadierId brigadier user id
 * @param name        brigadier name
 * @param zoneId      zone where the brigadier works
 * @param active      {@code true} if the brigadier is active, {@code false} if deactivated
 */
public record BrigadierInfo(String brigadierId, String name, String zoneId, boolean active) {
}
