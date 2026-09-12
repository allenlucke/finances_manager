package llc.feelingfroggy.finances.api;

import llc.feelingfroggy.finances.repo.StatementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reconciliation checkpoints. Read through the reconciliation report; removed here.
 *
 * <p>A checkpoint is the institution's own closing balance for a period, and it is metadata about
 * the ledger rather than money in it — so the delete is a real delete, not a soft one. It exists
 * because a wrong checkpoint used to be permanent: unique per account and period, created only
 * by import, and with no way to remove one, a figure read from a file the wrong way round accused a
 * correct ledger of being out until the end of time. A re-import now replaces a checkpoint it
 * disagrees with; this is for the one no file will ever replace.
 */
@RestController
@RequestMapping("/api/v1/statements")
public class StatementController {

    private final StatementRepository statements;
    private final CurrentUser currentUser;

    public StatementController(StatementRepository statements, CurrentUser currentUser) {
        this.statements = statements;
        this.currentUser = currentUser;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        var statement = statements.findByIdAndUserId(id, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        statements.delete(statement);
    }
}
