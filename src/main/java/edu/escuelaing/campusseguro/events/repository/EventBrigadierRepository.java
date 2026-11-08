package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventBrigadierEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class that access to the local copy of the brigadiers of each event.
 */
public interface EventBrigadierRepository extends JpaRepository<EventBrigadierEntity, UUID> {

    List<EventBrigadierEntity> findByEventId(UUID eventId);

    Optional<EventBrigadierEntity> findByEventIdAndBrigadierId(UUID eventId, String brigadierId);
}
