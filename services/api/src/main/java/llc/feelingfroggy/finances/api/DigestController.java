package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import llc.feelingfroggy.finances.domain.Reminder;
import llc.feelingfroggy.finances.service.DigestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** What needs a look, and the reminders that feed it (M8, D-20). */
@RestController
@RequestMapping("/api/v1")
public class DigestController {

    private final DigestService service;
    private final CurrentUser currentUser;

    public DigestController(DigestService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** The list the dashboard shows and the push sends: same code, same words. */
    @GetMapping("/digest/preview")
    public List<DigestService.Item> preview() {
        return service.compose(currentUser.id());
    }

    @PostMapping("/digest/send")
    public DigestService.Run send() {
        return service.send(currentUser.id(), true);
    }

    @GetMapping("/digest/runs")
    public List<DigestService.Run> runs(@RequestParam(defaultValue = "20") int size) {
        return service.runs(currentUser.id(), size);
    }

    @GetMapping("/digest/settings")
    public DigestService.Preferences settings() {
        return service.preferences(currentUser.id());
    }

    @PutMapping("/digest/settings")
    public DigestService.Preferences saveSettings(@RequestBody Settings s) {
        var current = service.preferences(currentUser.id());
        return service.savePreferences(currentUser.id(), new DigestService.Preferences(
            s.digestEnabled() == null ? current.digestEnabled() : s.digestEnabled(),
            s.digestTime() == null ? current.digestTime() : s.digestTime(),
            s.staleAfterDays() == null ? current.staleAfterDays() : s.staleAfterDays(),
            s.draftWaitHours() == null ? current.draftWaitHours() : s.draftWaitHours(),
            s.quietWhenEmpty() == null ? current.quietWhenEmpty() : s.quietWhenEmpty()));
    }

    @GetMapping("/reminders")
    public List<ReminderView> reminders() {
        return service.reminders(currentUser.id()).stream().map(ReminderView::of).toList();
    }

    @PostMapping("/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderView add(@Valid @RequestBody NewReminder r) {
        return ReminderView.of(service.addReminder(currentUser.id(), r.title(), r.notes(), r.dueOn(),
            r.cadence(), r.leadDays(), r.amount()));
    }

    @PutMapping("/reminders/{id}")
    public ReminderView update(@PathVariable Long id, @RequestBody EditReminder r) {
        return ReminderView.of(service.updateReminder(currentUser.id(), id, null, r.title(), r.notes(),
            r.dueOn(), r.cadence(), r.leadDays(), r.amount(), r.active()));
    }

    @PostMapping("/reminders/{id}/done")
    public ReminderView done(@PathVariable Long id) {
        return ReminderView.of(service.completeReminder(currentUser.id(), id));
    }

    @DeleteMapping("/reminders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteReminder(currentUser.id(), id);
    }

    public record Settings(Boolean digestEnabled, LocalTime digestTime, Integer staleAfterDays,
                           Integer draftWaitHours, Boolean quietWhenEmpty) {
    }

    public record NewReminder(@NotBlank @Size(max = 120) String title, @Size(max = 2000) String notes,
                              @NotNull LocalDate dueOn, String cadence, Integer leadDays, BigDecimal amount) {
    }

    public record EditReminder(String title, String notes, LocalDate dueOn, String cadence, Integer leadDays,
                               BigDecimal amount, Boolean active) {
    }

    public record ReminderView(Long id, String title, String notes, LocalDate dueOn, String cadence, int leadDays,
                               BigDecimal amount, boolean active, LocalDate lastDoneOn) {
        static ReminderView of(Reminder r) {
            return new ReminderView(r.getId(), r.getTitle(), r.getNotes(), r.getDueOn(), r.getCadence(),
                r.getLeadDays(), r.getAmount(), r.isActive(), r.getLastDoneOn());
        }
    }
}
