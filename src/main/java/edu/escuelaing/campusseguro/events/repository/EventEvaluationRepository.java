package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventEvaluationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Access to the automatic evaluations of finished events.
 */
public interface EventEvaluationRepository extends JpaRepository<EventEvaluationEntity, UUID> {

    Optional<EventEvaluationEntity> findByEventId(UUID eventId);

    /** Evaluations that still miss the CHI or the BEI numbers (completed by a scheduled job). */
    List<EventEvaluationEntity> findByTotalCheckinsIsNullOrTotalIncidentsIsNull();
}
