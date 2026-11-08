package edu.escuelaing.campusseguro.events.client.checkin;

import java.util.List;

/**
 * One page of check-ins.
 *
 * @param page  page number (starts at 0)
 * @param size  page size
 * @param total all check-ins that match
 * @param items check-ins of this page
 */
public record CheckInPage(int page, int size, long total, List<CheckInItem> items) {
}
