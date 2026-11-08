package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Access to the ALE alerts linked to each event.
 */
public interface EventAlertRepository extends JpaRepository<EventAlertEntity, UUID> {

    List<EventAlertEntity> findByEventId(UUID eventId);

    /** Alerts of an event with a given purpose (e.g. the one scheduled for the start). */
    List<EventAlertEntity> findByEventIdAndPurpose(UUID eventId, EventAlertPurpose purpose);
}
