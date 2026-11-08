package edu.escuelaing.campusseguro.events.entity.enums;

/** Kind of an audit record (RF-BEI-06): creation, modification or finalization. */
public enum AuditRecordType {

    /** Something was created (an event or an event type). */
    CREATION,
    /** Something was modified (an edit or a change of status other than finishing). */
    MODIFICATION,
    /** An event was finished. */
    FINALIZATION
}
