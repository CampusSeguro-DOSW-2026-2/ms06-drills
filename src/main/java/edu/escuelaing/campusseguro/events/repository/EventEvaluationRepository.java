package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventEvaluationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class that access to the automatic evaluations of finished events.
 */
public interface EventEvaluationRepository extends JpaRepository<EventEvaluationEntity, UUID> {

    Optional<EventEvaluationEntity> findByEventId(UUID eventId);
}
