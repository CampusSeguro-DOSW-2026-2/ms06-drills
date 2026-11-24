package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;

/**
 * Audit of the relevant actions (RF-BEI-06) through a local outbox ({@code pending_audit_records}).
 * The facts are sent to MS08 with its ingestion contract ({@code POST /api/v1/audit/events}).
 *
 * <p>{@link #record} joins the transaction of the action it audits: if the action is rolled back,
 * the record is rolled back too, so only operations that really happened are audited.
 * {@link #deliverPending} sends the saved records to MS08 later, so a MS08 outage never blocks an
 * operation.
 */
public interface AuditService {

    /**
     * Saves the record of a successful operation.
     *
     * @param type   creation, modification or finalization
     * @param action what was done (for example EVENT_CREATED, EVENT_STARTED)
     * @param actor  user or service that did it
     * @param event  event where it happened; {@code null} if there is none
     * @param detail generic code of the change (for example SCHEDULED_TO_IN_PROGRESS), at most 100
     *               characters, never free text or personal data; may be {@code null}
     */
    default void record(AuditRecordType type, String action, String actor, EventEntity event, String detail) {
        record(type, action, actor, event, detail, AuditExtras.NONE);
    }

    /**
     * Same as above with the extra data of the audit catalog: resource, attributes and the real time of the
     * action (for example {@code startedAt} for EVENT_STARTED).
     */
    void record(AuditRecordType type, String action, String actor, EventEntity event, String detail,
            AuditExtras extras);

    /**
     * Sends the oldest pending records to MS08, in order. It stops at the first record that MS08 does
     * not accept, so the order is kept; that record stays pending with its error. A record that MS08
     * rejects with 400 is closed with its reason and skipped, because sending it again would fail again.
     *
     * @return how many records were delivered
     */
    int deliverPending();
}
