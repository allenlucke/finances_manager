package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * Identity, optimistic locking, and audit timestamps.
 *
 * <p>{@code createdAt} and {@code updatedAt} are read-only here on purpose. The database sets both:
 * a DEFAULT on insert and the {@code set_updated_at()} trigger on update. Letting Hibernate write
 * them too would mean two sources of truth for the same column, and would leave direct SQL and
 * Flyway-driven changes with stale values.
 *
 * <p>Equality is by identity, and deliberately not by {@code id}: two unsaved entities both have a
 * null id and are not the same object. Entities go in lists, not hash sets, throughout this
 * codebase.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** True once the row exists in the database. */
    public boolean isPersisted() {
        return id != null;
    }

    @Override
    public final boolean equals(Object other) {
        return this == other;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(System.identityHashCode(this));
    }
}
