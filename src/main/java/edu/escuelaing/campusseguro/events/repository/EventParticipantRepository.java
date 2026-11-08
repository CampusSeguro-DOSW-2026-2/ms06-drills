package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventParticipantEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class that access to the roles invited to the events.
 */
public interface EventParticipantRepository extends JpaRepository<EventParticipantEntity, UUID> {

    List<EventParticipantEntity> findByEventId(UUID eventId);

    /** Deletes all invited roles of an event (they are replaced completely on edit). */
    void deleteByEventId(UUID eventId);
}
