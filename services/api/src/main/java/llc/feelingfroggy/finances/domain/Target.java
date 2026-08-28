package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An effective-dated intent for a category: a budget on an expense category, a forecast on an
 * income one. Replaces the legacy {@code budget}, both junction tables, and
 * {@code incomeItem.amountExpected} — expected-vs-received and budgeted-vs-spent are the same
 * question, so they get the same mechanism.
 *
 * <p>Set it once and it applies until changed. There are no per-period rows to create and nothing
 * to "close", which is the whole reason user-defined periods could be deleted.
 *
 * <p>Targets are <em>optional</em>. Most categories should need none: baselines derived from the
 * user's own history (M3/M6) cover the common case at zero effort. A target exists to express
 * intent that contradicts history — "cut dining to $200" — which cannot be derived from the data.
 *
 * <p>Changing a target means closing the current row and opening a new one; a database exclusion
 * constraint refuses overlapping ranges for the same category and entity, so a report can never
 * silently pick between two contradictory numbers. The closed rows are free budget history.
 */
@Entity
@Table(name = "target")
public class Target extends UserOwned {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_entity_id", nullable = false)
    private LedgerEntity ledgerEntity;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Convert(converter = Cadence.Conv.class)
    @Column(name = "cadence", nullable = false, length = 20)
    private Cadence cadence = Cadence.MONTHLY;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null means still in effect. Exclusive upper bound. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "note")
    private String note;

    protected Target() {
    }

    public Target(Long userId, Category category, LedgerEntity ledgerEntity, BigDecimal amount,
                  LocalDate effectiveFrom) {
        super(userId);
        this.category = category;
        this.ledgerEntity = ledgerEntity;
        this.amount = amount;
        this.effectiveFrom = effectiveFrom;
    }

    /** True if this target governs {@code date}. Upper bound is exclusive. */
    public boolean appliesOn(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveTo == null || date.isBefore(effectiveTo));
    }

    /** Closes this target so a replacement can start on the same day without overlapping. */
    public void closeOn(LocalDate date) {
        this.effectiveTo = date;
    }

    public Category getCategory() {
        return category;
    }

    public LedgerEntity getLedgerEntity() {
        return ledgerEntity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Cadence getCadence() {
        return cadence;
    }

    public void setCadence(Cadence cadence) {
        this.cadence = cadence;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
