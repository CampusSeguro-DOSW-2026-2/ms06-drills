package edu.escuelaing.campusseguro.events.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Class that tests the audit dates that {@link BaseEntity} fills on save. */
class EntityTimestampsTest {

    @Test
    void fillsBothDatesOnCreation() {
        EventTypeEntity entity = new EventTypeEntity();

        entity.initTimestamps();

        assertNotNull(entity.getCreatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Test
    void keepsDatesThatWereAssignedBefore() {
        Instant fixed = Instant.parse("2026-11-07T12:00:00Z");
        EventTypeEntity entity = new EventTypeEntity();
        entity.setCreatedAt(fixed);
        entity.setUpdatedAt(fixed.plusSeconds(5));

        entity.initTimestamps();

        assertEquals(fixed, entity.getCreatedAt());
        assertEquals(fixed.plusSeconds(5), entity.getUpdatedAt());
    }

    @Test
    void updateMovesOnlyTheModificationDate() {
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        EventTypeEntity entity = new EventTypeEntity();
        entity.setCreatedAt(old);
        entity.setUpdatedAt(old);

        entity.touch();

        assertEquals(old, entity.getCreatedAt());
        assertTrue(entity.getUpdatedAt().isAfter(old));
    }
}
