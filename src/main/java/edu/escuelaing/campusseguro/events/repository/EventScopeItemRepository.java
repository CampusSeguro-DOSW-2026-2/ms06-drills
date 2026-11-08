package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventScopeItemEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class that access to the scope items (buildings and zones) of the events.
 */
public interface EventScopeItemRepository extends JpaRepository<EventScopeItemEntity, UUID> {

    List<EventScopeItemEntity> findByEventId(UUID eventId);

    /** Deletes the whole scope of an event (it is replaced completely on edit). */
    void deleteByEventId(UUID eventId);
}
