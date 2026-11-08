package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;

/**
 * Audit of the relevant actions (RF-BEI-06) through a local outbox ({@code pending_audit_records}).
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
     * @param action what was done (for example EVENT_CREATED)
     * @param actor  user or service that did it
     * @param event  event where it happened; {@code null} if there is none
     * @param detail short explanation; may be {@code null}
     */
    void record(AuditRecordType type, String action, String actor, EventEntity event, String detail);

    /**
     * Sends the oldest pending records to MS08, in order. It stops at the first record that MS08 does
     * not accept, so the order is kept; that record stays pending with its error.
     *
     * @return how many records were delivered
     */
    int deliverPending();
}
