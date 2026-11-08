package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.PendingAuditEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class that access to the audit outbox sent to MS08.
 */
public interface PendingAuditRepository extends JpaRepository<PendingAuditEntity, UUID> {

    /** Up to 100 records not delivered yet, from the oldest to the newest. */
    List<PendingAuditEntity> findTop100BySentAtIsNullOrderByOccurredAtAsc();
}
