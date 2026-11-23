package edu.escuelaing.campusseguro.events.service;

import java.time.Instant;
import java.util.Map;

/**
 * Optional data of an audit fact, as listed in the MS06 block of the audit catalog.
 *
 * @param resource   affected resource as {@code type:id}; {@code null} = {@code evento:<id>} of the event
 *                   (or no resource when there is no event)
 * @param attributes flat extra data (snake_case keys; text, number or boolean values); {@code null} = none
 * @param occurredAt when the action happened; {@code null} = now (for example the real {@code startedAt})
 */
public record AuditExtras(String resource, Map<String, Object> attributes, Instant occurredAt) {

    /** No extra data: the default resource, no attributes and the current time. */
    public static final AuditExtras NONE = new AuditExtras(null, null, null);

    /** Only attributes; the resource and the time stay the default. */
    public static AuditExtras attributes(Map<String, Object> attributes) {
        return new AuditExtras(null, attributes, null);
    }
}
