package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class that access to the status change history of the events.
 */
public interface EventStateHistoryRepository extends JpaRepository<EventStateHistoryEntity, UUID> {

    /** History of an event, from the oldest change to the newest. */
    List<EventStateHistoryEntity> findByEventIdOrderByChangedAtAsc(UUID eventId);
}
