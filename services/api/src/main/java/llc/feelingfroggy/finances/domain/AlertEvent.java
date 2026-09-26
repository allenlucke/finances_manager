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

/**
 * One firing of a {@link PriceAlert}, and whether the notification got through (M7a).
 *
 * <p>Delivery is recorded separately from the firing: an alert that fired and could not be
 * delivered is still an alert that fired, and the screen shows both.
 */
@Entity
@Table(name = "alert_event")
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private PriceAlert alert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id")
    private Quote quote;

    @Column(name = "fired_at", nullable = false)
    private Instant firedAt;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "delivered", nullable = false)
    private boolean delivered;

    @Column(name = "delivery_error")
    private String deliveryError;

    protected AlertEvent() {
    }

    public AlertEvent(Long userId, PriceAlert alert, Quote quote, Instant firedAt, String message) {
        this.userId = userId;
        this.alert = alert;
        this.quote = quote;
        this.firedAt = firedAt;
        this.message = message;
    }

    public void delivered() {
        this.delivered = true;
        this.deliveryError = null;
    }

    public void notDelivered(String why) {
        this.delivered = false;
        this.deliveryError = why;
    }

    public Long getId() {
        return id;
    }

    public PriceAlert getAlert() {
        return alert;
    }

    public Quote getQuote() {
        return quote;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    public String getMessage() {
        return message;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public String getDeliveryError() {
        return deliveryError;
    }
}
