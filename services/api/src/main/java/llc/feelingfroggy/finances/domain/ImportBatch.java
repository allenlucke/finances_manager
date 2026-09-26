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
import java.time.Instant;
import java.util.List;

/**
 * One statement-import run. Populated in M2; it exists now so {@link Transaction} never needs an
 * altering migration to gain its provenance link.
 *
 * <p>Does not extend {@link BaseEntity}: an import batch is an immutable event record with no
 * {@code updated_at} and nothing to lock optimistically.
 *
 * <p>The uploaded file is deliberately not stored here. Per docs/SECURITY.md statement files are
 * kept only as long as the import needs them, then deleted or moved to encrypted storage.
 */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private SourceConnection connection;

    /** Nullable: a single OFX file can legitimately span several accounts. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Convert(converter = ImportFormat.Conv.class)
    @Column(name = "source_format", nullable = false, length = 20)
    private ImportFormat sourceFormat;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Convert(converter = ImportStatus.Conv.class)
    @Column(name = "status", nullable = false, length = 20)
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "applied_count", nullable = false)
    private int appliedCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    /** Rows categorized without a person, and rows left with a suggestion to review (M3a). */
    @Column(name = "auto_categorized", nullable = false)
    private int autoCategorized;

    @Column(name = "suggested", nullable = false)
    private int suggested;

    @Column(name = "error")
    private String error;

    /**
     * What the parser said about the file, one note per line: rows it could not read, an encoding
     * it had to fall back to, a statement it could not checkpoint. Null when it had nothing to say.
     * Shown to the person, never counted into a log line — see V8.
     */
    @Column(name = "warnings")
    private String warnings;

    @Column(name = "started_at", insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportBatch() {
    }

    public ImportBatch(Long userId, ImportFormat sourceFormat) {
        this.userId = userId;
        this.sourceFormat = sourceFormat;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public SourceConnection getConnection() {
        return connection;
    }

    public void setConnection(SourceConnection connection) {
        this.connection = connection;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public ImportFormat getSourceFormat() {
        return sourceFormat;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public void setAppliedCount(int appliedCount) {
        this.appliedCount = appliedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<String> getWarnings() {
        return warnings == null || warnings.isBlank() ? List.of() : List.of(warnings.split("\n"));
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null || warnings.isEmpty() ? null : String.join("\n", warnings);
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getAutoCategorized() {
        return autoCategorized;
    }

    public int getSuggested() {
        return suggested;
    }

    public void setCategorizationCounts(int autoCategorized, int suggested) {
        this.autoCategorized = autoCategorized;
        this.suggested = suggested;
    }
}
