package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A category suggestion and how a human resolved it.
 *
 * <p>Applied in M3, but present from the start because D-15 requires corrections to be captured
 * rather than merely applied: they are both the tier-2 similarity training signal and the only way
 * M3 can <em>measure</em> accuracy instead of asserting it. A correction that was applied but not
 * recorded is a lost training example.
 *
 * <p>At most one unresolved suggestion may exist per transaction — re-running the categorizer
 * replaces the open one rather than stacking a second, which a partial unique index enforces.
 */
@Entity
@Table(name = "categorization")
public class Categorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    /**
     * The owner, denormalized from the transaction so the composite tenant FKs (V7) can hold. Every
     * other scoped table had this from V2; this one was built without it — the case V2's own
     * header calls "expensive to retrofit once data exists" — and M3 has not written a row yet, so
     * it was cheap after all.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_category_id")
    private Category suggestedCategory;

    /** The categorizer believes this is a transfer, i.e. not an expense at all. */
    @Column(name = "suggested_transfer", nullable = false)
    private boolean suggestedTransfer;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Convert(converter = CategorizationMethod.Conv.class)
    @Column(name = "method", nullable = false, length = 20)
    private CategorizationMethod method;

    /** Which model produced this, when the method is MODEL. Null for rules and similarity. */
    @Column(name = "model_id", length = 80)
    private String modelId;

    @Column(name = "rationale")
    private String rationale;

    @Convert(converter = Resolution.Conv.class)
    @Column(name = "resolution", length = 20)
    private Resolution resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_category_id")
    private Category resolvedCategory;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Categorization() {
    }

    public Categorization(Transaction transaction, Category suggestedCategory,
                          BigDecimal confidence, CategorizationMethod method) {
        this.transaction = transaction;
        this.suggestedCategory = suggestedCategory;
        this.confidence = confidence;
        this.method = method;
    }

    /** The suggestion was right. */
    public void accept(Instant when) {
        this.resolution = Resolution.ACCEPTED;
        this.resolvedCategory = suggestedCategory;
        this.resolvedAt = when;
    }

    /**
     * The suggestion was wrong and the human supplied the right answer. This is the highest-value
     * training signal in the system, which is why the target category is mandatory.
     */
    public void correct(Category actual, Instant when) {
        if (actual == null) {
            throw new IllegalArgumentException(
                "A correction must name the category it was corrected to, or the training signal "
                    + "is lost.");
        }
        this.resolution = Resolution.CORRECTED;
        this.resolvedCategory = actual;
        this.resolvedAt = when;
    }

    /** Neither the suggestion nor any category applies — typically because it is a transfer. */
    public void reject(Instant when) {
        this.resolution = Resolution.REJECTED;
        this.resolvedCategory = null;
        this.resolvedAt = when;
    }

    public boolean isResolved() {
        return resolution != null;
    }

    public Long getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Category getSuggestedCategory() {
        return suggestedCategory;
    }

    public boolean isSuggestedTransfer() {
        return suggestedTransfer;
    }

    public void setSuggestedTransfer(boolean suggestedTransfer) {
        this.suggestedTransfer = suggestedTransfer;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public CategorizationMethod getMethod() {
        return method;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public Category getResolvedCategory() {
        return resolvedCategory;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
