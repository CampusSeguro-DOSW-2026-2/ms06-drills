package edu.escuelaing.campusseguro.events.client.campus;

import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;

/**
 * Evacuation route map of a building or zone, as CAM gives it.
 *
 * @param type  building or zone
 * @param refId identifier in CAM
 * @param name  display name
 * @param url   where the map can be opened
 */
public record ScopeRoute(ScopeType type, String refId, String name, String url) {
}
