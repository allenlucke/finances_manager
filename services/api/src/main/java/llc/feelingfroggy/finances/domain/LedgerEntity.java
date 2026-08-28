package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Personal vs. Feeling Froggy LLC.
 *
 * <p>A table rather than a boolean because an entity has a name, will later carry tax attributes,
 * and a third entity should not require a migration. See docs/DOMAIN.md → Entity scoping.
 */
@Entity
@Table(name = "ledger_entity")
public class LedgerEntity extends UserOwned {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Convert(converter = EntityKind.Conv.class)
    @Column(name = "kind", nullable = false, length = 20)
    private EntityKind kind;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected LedgerEntity() {
    }

    public LedgerEntity(Long userId, String name, EntityKind kind) {
        super(userId);
        this.name = name;
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EntityKind getKind() {
        return kind;
    }

    public void setKind(EntityKind kind) {
        this.kind = kind;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
