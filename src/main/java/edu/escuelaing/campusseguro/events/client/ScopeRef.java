package edu.escuelaing.campusseguro.events.client;

import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;

/**
 * Reference to a building or zone of the campus (ids come from CAM).
 *
 * @param type building or zone
 * @param id   identifier in CAM
 */
public record ScopeRef(ScopeType type, String id) {
}
