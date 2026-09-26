package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** A dated thing the person asked to be told about, once or on a cadence (M8). */
@Entity
@Table(name = "reminder")
public class Reminder extends UserOwned {

    public static final Set<String> CADENCES = Set.of("once", "weekly", "monthly", "quarterly", "yearly");

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "notes")
    private String notes;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "cadence", nullable = false, length = 10)
    private String cadence;

    /** How many days before the due date the digest starts mentioning it. */
    @Column(name = "lead_days", nullable = false)
    private int leadDays;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_done_on")
    private LocalDate lastDoneOn;

    protected Reminder() {
    }

    public Reminder(Long userId, String title, String notes, LocalDate dueOn, String cadence,
                    Integer leadDays, BigDecimal amount) {
        super(userId);
        if (title == null || title.isBlank()) {
            throw new DomainRuleViolation("A reminder needs a title");
        }
        if (dueOn == null) {
            throw new DomainRuleViolation("A reminder needs a due date");
        }
        String c = cadence == null ? "once" : cadence;
        if (!CADENCES.contains(c)) {
            throw new DomainRuleViolation("Cadence is once, weekly, monthly, quarterly or yearly");
        }
        int lead = leadDays == null ? 3 : leadDays;
        if (lead < 0 || lead > 90) {
            throw new DomainRuleViolation("Lead days is between 0 and 90");
        }
        if (amount != null && amount.signum() < 0) {
            throw new DomainRuleViolation("A reminder's amount is not negative");
        }
        this.title = title.strip();
        this.notes = notes;
        this.dueOn = dueOn;
        this.cadence = c;
        this.leadDays = lead;
        this.amount = amount;
    }

    /**
     * Done: a one-off goes quiet; a recurring one moves to its next occurrence — the one after
     * this due date, and past today if several were missed. Doing it early still counts.
     */
    public void complete(LocalDate today) {
        this.lastDoneOn = today;
        if ("once".equals(cadence)) {
            this.active = false;
            return;
        }
        LocalDate next = advance(dueOn);
        while (!next.isAfter(today)) {
            next = advance(next);
        }
        this.dueOn = next;
    }

    private LocalDate advance(LocalDate from) {
        return switch (cadence) {
            case "weekly" -> from.plusWeeks(1);
            case "monthly" -> from.plusMonths(1);
            case "quarterly" -> from.plusMonths(3);
            case "yearly" -> from.plusYears(1);
            default -> from;
        };
    }

    /** Due within its lead time, or overdue. */
    public boolean isDue(LocalDate today) {
        return active && !dueOn.isAfter(today.plusDays(leadDays));
    }

    public String describe(LocalDate today) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, dueOn);
        String when = days < 0 ? "overdue by " + (-days) + " day" + (days == -1 ? "" : "s")
            : days == 0 ? "due today"
            : days == 1 ? "due tomorrow"
            : "due in " + days + " days (" + dueOn + ")";
        return title + (amount == null ? "" : " (" + amount.setScale(2, java.math.RoundingMode.HALF_UP) + ")") + ": " + when;
    }

    public void update(String title, String notes, LocalDate dueOn, String cadence, Integer leadDays,
                       BigDecimal amount, Boolean active) {
        if (title != null && !title.isBlank()) {
            this.title = title.strip();
        }
        if (notes != null) {
            this.notes = notes;
        }
        if (dueOn != null) {
            this.dueOn = dueOn;
        }
        if (cadence != null) {
            if (!CADENCES.contains(cadence)) {
                throw new DomainRuleViolation("Cadence is once, weekly, monthly, quarterly or yearly");
            }
            this.cadence = cadence;
        }
        if (leadDays != null) {
            if (leadDays < 0 || leadDays > 90) {
                throw new DomainRuleViolation("Lead days is between 0 and 90");
            }
            this.leadDays = leadDays;
        }
        if (amount != null) {
            this.amount = amount.signum() == 0 ? null : amount;
        }
        if (active != null) {
            this.active = active;
        }
    }

    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public LocalDate getDueOn() { return dueOn; }
    public String getCadence() { return cadence; }
    public int getLeadDays() { return leadDays; }
    public BigDecimal getAmount() { return amount; }
    public boolean isActive() { return active; }
    public LocalDate getLastDoneOn() { return lastDoneOn; }
}
