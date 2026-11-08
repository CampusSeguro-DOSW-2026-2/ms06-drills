package edu.escuelaing.campusseguro.events.client.checkin;

import java.time.Instant;

/**
 * One check-in of a person.
 *
 * @param userId       person
 * @param name         person name
 * @param status       check-in status (CHI's name)
 * @param zoneId       zone where it was registered
 * @param registeredAt when it was registered
 */
public record CheckInItem(String userId, String name, String status, String zoneId, Instant registeredAt) {
}
