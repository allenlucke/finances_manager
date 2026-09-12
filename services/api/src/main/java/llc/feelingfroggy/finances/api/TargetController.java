package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import llc.feelingfroggy.finances.domain.Cadence;
import llc.feelingfroggy.finances.domain.Target;
import llc.feelingfroggy.finances.repo.CategoryRepository;
import llc.feelingfroggy.finances.repo.LedgerEntityRepository;
import llc.feelingfroggy.finances.repo.TargetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Budgets and income forecasts, as effective-dated rows.
 *
 * <p>There is no "update a target" operation on purpose. Changing an amount closes the current row
 * and opens a new one, which keeps the history of what the budget used to be — free, and needed by
 * any honest comparison across months.
 */
@RestController
@RequestMapping("/api/v1/targets")
public class TargetController {

    private final TargetRepository targets;
    private final CategoryRepository categories;
    private final LedgerEntityRepository entities;
    private final CurrentUser currentUser;
    private final java.time.Clock clock;

    public TargetController(TargetRepository targets, CategoryRepository categories,
                            LedgerEntityRepository entities, CurrentUser currentUser,
                            java.time.Clock clock) {
        this.targets = targets;
        this.categories = categories;
        this.entities = entities;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping
    public List<TargetView> list() {
        return targets.findByUserIdOrderByEffectiveFromDesc(currentUser.id())
            .stream().map(TargetView::of).toList();
    }

    /**
     * Sets the target for a category and entity from a date forward.
     *
     * <p>Closes whatever is currently open at the same date first. Without that the database's
     * exclusion constraint would reject the insert — correctly, since two overlapping targets have
     * no defined answer — so doing it here turns a 409 into the operation the caller meant.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TargetView set(@Valid @RequestBody SetTarget request) {
        Long userId = currentUser.id();
        var category = categories.findByIdAndUserId(request.categoryId(), userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category"));
        var entity = entities.findByIdAndUserId(request.ledgerEntityId(), userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown entity"));

        // The configured zone, not the container's UTC — see ClockConfig.
        LocalDate from = request.effectiveFrom() == null ? LocalDate.now(clock) : request.effectiveFrom();

        targets.findOpen(userId, category.getId(), entity.getId()).ifPresent(open -> {
            if (!open.getEffectiveFrom().isBefore(from)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A target for this category already starts on or after " + from);
            }
            open.closeOn(from);
            targets.save(open);
        });
        // Flush the close before the insert, or the exclusion constraint sees both as open.
        targets.flush();

        var target = new Target(userId, category, entity, request.amount(), from);
        if (request.cadence() != null) {
            target.setCadence(request.cadence());
        }
        target.setNote(request.note());
        return TargetView.of(targets.save(target));
    }

    public record TargetView(Long id, Long categoryId, Long ledgerEntityId, BigDecimal amount,
                             String cadence, LocalDate effectiveFrom, LocalDate effectiveTo,
                             String note) {
        static TargetView of(Target target) {
            return new TargetView(target.getId(), target.getCategory().getId(),
                target.getLedgerEntity().getId(), target.getAmount(), target.getCadence().code(),
                target.getEffectiveFrom(), target.getEffectiveTo(), target.getNote());
        }
    }

    public record SetTarget(
        @NotNull Long categoryId,
        @NotNull Long ledgerEntityId,
        @NotNull @PositiveOrZero BigDecimal amount,
        Cadence cadence,
        LocalDate effectiveFrom,
        String note) {
    }
}
