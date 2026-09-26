package llc.feelingfroggy.finances.api;

import java.util.List;
import llc.feelingfroggy.finances.service.RecurringService;
import llc.feelingfroggy.finances.service.SnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The ledger's rhythm (M9, D-21): recurring charges, the weeks ahead, and net worth over time. */
@RestController
@RequestMapping("/api/v1")
public class CashflowController {

    private final RecurringService recurring;
    private final SnapshotService snapshots;
    private final CurrentUser currentUser;

    public CashflowController(RecurringService recurring, SnapshotService snapshots, CurrentUser currentUser) {
        this.recurring = recurring;
        this.snapshots = snapshots;
        this.currentUser = currentUser;
    }

    /** Recurring series with their evidence, what is expected in the window, and anomalies. */
    @GetMapping("/cashflow")
    public RecurringService.Report cashflow(@RequestParam(defaultValue = "30") int days) {
        return recurring.report(currentUser.id(), Math.min(Math.max(days, 1), 365));
    }

    /** "This is not a recurring charge" — or it is after all. Remembered by the series key. */
    @org.springframework.web.bind.annotation.PutMapping("/cashflow/mute")
    public RecurringService.Report setMuted(@org.springframework.web.bind.annotation.RequestBody Mute request) {
        recurring.setMuted(currentUser.id(), request.key(), request.label(), request.muted());
        return recurring.report(currentUser.id(), 30);
    }

    public record Mute(String key, String label, boolean muted) {
    }

    @GetMapping("/reports/net-worth/history")
    public List<SnapshotService.Point> netWorthHistory(@RequestParam(defaultValue = "90") int days) {
        return snapshots.history(currentUser.id(), days);
    }

    /** Take today's snapshot now rather than waiting for the housekeeping tick. */
    @PostMapping("/reports/net-worth/snapshot")
    public List<SnapshotService.Point> snapshotNow() {
        snapshots.take(currentUser.id());
        return snapshots.history(currentUser.id(), 1);
    }
}
