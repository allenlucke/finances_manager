package llc.feelingfroggy.finances.service;

import java.time.Instant;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.ImportBatch;
import llc.feelingfroggy.finances.domain.ImportFormat;
import llc.feelingfroggy.finances.domain.ImportStatus;
import llc.feelingfroggy.finances.repo.ImportBatchRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the life of an import batch in transactions of its own.
 *
 * <p>This exists because of a bug the tests caught. The batch row was being written inside the same
 * transaction that applies the rows, so when an import failed, the rollback took the failure record
 * with it — leaving no trace of the very thing you most want a trace of. An audit record has to
 * outlive the failure it describes.
 *
 * <p>Hence {@code REQUIRES_NEW} on every method here, and hence a separate bean: Spring's
 * transaction proxy does not intercept a call a class makes to itself, so these could not simply be
 * private methods on {@link ImportService}.
 */
@Component
public class ImportBatchRecorder {

    private final ImportBatchRepository batches;

    public ImportBatchRecorder(ImportBatchRepository batches) {
        this.batches = batches;
    }

    /** Commits a pending batch immediately, so even a crash mid-import leaves a record. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long begin(Long userId, Account account, ImportFormat format, String filename) {
        var batch = new ImportBatch(userId, format);
        batch.setAccount(account);
        batch.setOriginalFilename(filename);
        return batches.save(batch).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch complete(Long batchId, int rowCount, int applied, int duplicates) {
        var batch = batches.findById(batchId).orElseThrow();
        batch.setRowCount(rowCount);
        batch.setAppliedCount(applied);
        batch.setDuplicateCount(duplicates);
        batch.setStatus(ImportStatus.APPLIED);
        batch.setCompletedAt(Instant.now());
        return batches.save(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long batchId, String reason) {
        batches.findById(batchId).ifPresent(batch -> {
            batch.setStatus(ImportStatus.FAILED);
            batch.setError(reason);
            batch.setCompletedAt(Instant.now());
            batches.save(batch);
        });
    }

    /**
     * Records something the user needs to know about an otherwise successful import — rows skipped
     * because no account matched, for instance. Kept on the batch rather than only in a log so it
     * survives to the import history screen.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void note(Long batchId, String message) {
        batches.findById(batchId).ifPresent(batch -> {
            batch.setError(message);
            batches.save(batch);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch load(Long batchId) {
        return batches.findById(batchId).orElseThrow();
    }
}
