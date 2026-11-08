package edu.escuelaing.campusseguro.events.repository;

import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Access to the event type catalog.
 */
public interface EventTypeRepository extends JpaRepository<EventTypeEntity, UUID> {

    /** Finds a type by its stable code (e.g. SIMULACRO). */
    Optional<EventTypeEntity> findByCode(String code);

    /** Active types sorted by name, to show them when creating an event. */
    List<EventTypeEntity> findByActiveTrueOrderByNameAsc();

    /** Types with the given identifiers (to validate those received in a request). */
    List<EventTypeEntity> findByIdIn(Collection<UUID> ids);

    /** Tells if a type with that code already exists. */
    boolean existsByCode(String code);

    /** Tells if a type with that name already exists, ignoring case. */
    boolean existsByNameIgnoreCase(String name);
}
