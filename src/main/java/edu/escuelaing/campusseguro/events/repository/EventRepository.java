package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to events. The status queries serve the start scheduler and the BEI sync.
 * Several events can be In progress at the same time.
 */
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    /**
     * Loads the event with a pessimistic write lock, so two simultaneous
     * actions (e.g. edit and finish) cannot change its status at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventEntity e where e.id = :id")
    Optional<EventEntity> findForUpdate(@Param("id") UUID id);

    /** Events in the given status whose scheduled start time has arrived (start scheduler). */
    List<EventEntity> findByStatusAndScheduledStartLessThanEqual(EventStatus status, Instant moment);

    /**
     * Searches events by name and status, one page at a time.
     *
     * @param pattern  lower-case LIKE pattern, already escaped with {@code !} (for example {@code %fire!_drill%})
     * @param statuses statuses to include (never empty)
     */
    @Query("select e from EventEntity e where e.status in :statuses "
            + "and lower(e.name) like :pattern escape '!'")
    Page<EventEntity> search(
            @Param("pattern") String pattern,
            @Param("statuses") Collection<EventStatus> statuses,
            Pageable pageable);

    /** Events in the given status, most recently started first (events in progress). */
    List<EventEntity> findByStatusOrderByStartedAtDesc(EventStatus status);

    /** Events in any of the given statuses (BEI sync). */
    List<EventEntity> findByStatusIn(Collection<EventStatus> statuses);
}
