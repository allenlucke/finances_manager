package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One table replacing the legacy {@code expenseCategory} + {@code incomeCategory}, which were the
 * same table twice with a different word in the name.
 *
 * <p>{@code parent} allows grouping ("Utilities" → "Electric"). Names are unique per parent rather
 * than globally, so "Utilities → Electric" and "Home → Electric" can coexist.
 */
@Entity
@Table(name = "category")
public class Category extends UserOwned {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Convert(converter = CategoryKind.Conv.class)
    @Column(name = "kind", nullable = false, length = 20)
    private CategoryKind kind;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public Category(Long userId, String name, CategoryKind kind) {
        super(userId);
        this.name = name;
        this.kind = kind;
    }

    public Category getParent() {
        return parent;
    }

    public void setParent(Category parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CategoryKind getKind() {
        return kind;
    }

    public void setKind(CategoryKind kind) {
        this.kind = kind;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
