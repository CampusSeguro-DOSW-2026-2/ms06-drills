package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventStartFailureEntity;
import edu.escuelaing.campusseguro.events.entity.enums.ExternalModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Access to the calls that failed when events started (see {@link EventStartFailureEntity}).
 */
public interface EventStartFailureRepository extends JpaRepository<EventStartFailureEntity, UUID> {

    /** Calls that are still pending, for all events (retry job). */
    List<EventStartFailureEntity> findByResolvedAtIsNull();

    /** Calls that are still pending for one event (event panel). */
    List<EventStartFailureEntity> findByEventIdAndResolvedAtIsNull(UUID eventId);

    /** The row of one module for one event, pending or resolved. */
    Optional<EventStartFailureEntity> findByEventIdAndModule(UUID eventId, ExternalModule module);
}
