package edu.escuelaing.campusseguro.events.client.brigade;

/**
 * Cases of one brigadier in an event.
 *
 * @param brigadierId   brigadier user id
 * @param casesAttended cases the brigadier attended
 * @param casesResolved cases the brigadier resolved
 */
public record BrigadierPerformance(String brigadierId, int casesAttended, int casesResolved) {
}
