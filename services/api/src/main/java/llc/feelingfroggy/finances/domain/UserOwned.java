package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Everything in this domain belongs to exactly one user.
 *
 * <p>{@code userId} is a plain column rather than a {@code @ManyToOne}: it exists to satisfy the
 * composite foreign keys described under TENANT INTEGRITY in {@code V2__core_domain.sql}, which
 * pair it with each row's parent reference — {@code (account_id, user_id)} must resolve to a real
 * {@code (id, user_id)} on {@code account}. Mapping it as an association would add a join that no
 * query wants, and the value is needed on insert regardless.
 *
 * <p>Because those FKs are enforced by the database, a service that sets the wrong {@code userId}
 * gets a constraint violation rather than silently reading another user's money.
 */
@MappedSuperclass
public abstract class UserOwned extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    protected UserOwned() {
    }

    protected UserOwned(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
