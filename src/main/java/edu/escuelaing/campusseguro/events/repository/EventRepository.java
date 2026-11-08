package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Class that access to events. The status queries serve the start scheduler and rule
 */
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    /**
     * Loads the event with a pessimistic write lock, so two simultaneous
     * actions (e.g. edit and finish) cannot change its status at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventEntity e where e.id = :id")
    Optional<EventEntity> findForUpdate(@Param("id") UUID id);

    /** Tells if any event exists in the given status (RN-9: one is In progress). */
    boolean existsByStatus(EventStatus status);

    /** Same as {@link #existsByStatus} but ignoring one event (the one trying to start). */
    boolean existsByStatusAndIdNot(EventStatus status, UUID id);

    /** Events in the given status whose scheduled start time has arrived (start scheduler). */
    List<EventEntity> findByStatusAndScheduledStartLessThanEqual(EventStatus status, Instant moment);

    /** Events in any of the given statuses (BEI sync). */
    List<EventEntity> findByStatusIn(Collection<EventStatus> statuses);
}
