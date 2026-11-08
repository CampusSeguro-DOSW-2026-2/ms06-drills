package edu.escuelaing.campusseguro.events.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

/**
 * Common base of the entities: creation and last modification dates.
 *
 * <p>Dates are filled automatically on save. Services that need a controlled
 * time (for example in tests) can set them first with the setters.
 */
@MappedSuperclass
public abstract class BaseEntity {

    /** Creation time; never changes. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Last modification time. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void initTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
