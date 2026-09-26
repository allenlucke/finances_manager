package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** One transition of a {@link TradeOrder}: who moved it, from what, to what, and why. */
@Entity
@Table(name = "trade_order_event")
public class TradeOrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private TradeOrder order;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "actor", nullable = false, length = 10)
    private String actor;

    @Column(name = "note")
    private String note;

    protected TradeOrderEvent() {
    }

    public TradeOrderEvent(Long userId, TradeOrder order, Instant at, String fromStatus,
                           String toStatus, String actor, String note) {
        this.userId = userId;
        this.order = order;
        this.at = at;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.note = note;
    }

    public Long getId() { return id; }
    public TradeOrder getOrder() { return order; }
    public Instant getAt() { return at; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getNote() { return note; }
}
