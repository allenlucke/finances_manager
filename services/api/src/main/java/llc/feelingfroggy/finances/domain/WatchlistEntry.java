package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A symbol the person is watching, held or not (M7a). */
@Entity
@Table(name = "watchlist")
public class WatchlistEntry extends UserOwned {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(name = "note")
    private String note;

    protected WatchlistEntry() {
    }

    public WatchlistEntry(Long userId, Security security, String note) {
        super(userId);
        this.security = security;
        this.note = note;
    }

    public Security getSecurity() {
        return security;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
