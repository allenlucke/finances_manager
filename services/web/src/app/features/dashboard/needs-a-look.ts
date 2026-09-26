import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { money, today } from '../../core/money';
import { DigestItem, Reminder, ReminderCadence } from '../../core/models';

type Detail = { error?: { detail?: string | null } | null };

/**
 * What needs a look, and the reminders that feed it (M8, D-20).
 *
 * <p>The list here is the list the daily push sends: same code on the server, same sentences.
 * A reminder is a dated thing the person asked to be told about; done on a recurring one moves
 * it to its next occurrence.
 */
@Component({
  selector: 'app-needs-a-look',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './needs-a-look.html',
  styleUrl: './needs-a-look.scss',
})
export class NeedsALookComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly items = new LoadState<DigestItem[]>('Could not work out what needs a look.');
  protected readonly reminders = new LoadState<Reminder[]>('Could not load reminders.');
  protected readonly sending = signal(false);
  protected readonly busy = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly money = money;

  protected readonly cadences: { value: ReminderCadence; label: string }[] = [
    { value: 'once', label: 'once' },
    { value: 'weekly', label: 'every week' },
    { value: 'monthly', label: 'every month' },
    { value: 'quarterly', label: 'every quarter' },
    { value: 'yearly', label: 'every year' },
  ];

  protected readonly form = this.forms.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(120)]],
    dueOn: [today(), Validators.required],
    cadence: ['once' as ReminderCadence, Validators.required],
    leadDays: ['3', [Validators.required, Validators.pattern(/^\d{1,2}$/)]],
    amount: ['', Validators.pattern(/^\d+(\.\d{1,2})?$/)],
  });

  protected readonly active = computed(() =>
    (this.reminders.value() ?? []).filter((r) => r.active),
  );
  protected readonly done = computed(() => (this.reminders.value() ?? []).filter((r) => !r.active));

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.items.run(this.api.digestPreview());
    this.reminders.run(this.api.reminders());
  }

  protected send(): void {
    if (this.sending()) return;
    this.sending.set(true);
    this.api.sendDigest().subscribe({
      next: (run) => {
        this.sending.set(false);
        this.notice.set(run.sent ? null : `Not sent: ${run.deliveryError}`);
        if (run.sent) {
          this.snackBar.open(
            run.items === 0 ? 'Sent: all quiet.' : `Sent ${run.items} thing(s) to your phone.`,
            undefined,
            { duration: 5000 },
          );
        }
      },
      error: (error: Detail) => {
        this.sending.set(false);
        this.notice.set(error?.error?.detail || 'Could not send the digest.');
      },
    });
  }

  protected add(): void {
    if (this.form.invalid || this.busy()) return;
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.api
      .addReminder({
        title: v.title.trim(),
        notes: null,
        dueOn: v.dueOn,
        cadence: v.cadence,
        leadDays: Number(v.leadDays),
        amount: v.amount || null,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notice.set(null);
          this.form.reset({
            title: '',
            dueOn: today(),
            cadence: v.cadence,
            leadDays: v.leadDays,
            amount: '',
          });
          this.reload();
        },
        error: (error: Detail) => {
          this.busy.set(false);
          this.notice.set(error?.error?.detail || 'Could not add the reminder.');
        },
      });
  }

  protected complete(reminder: Reminder): void {
    this.api.completeReminder(reminder.id).subscribe({
      next: (after) => {
        this.reload();
        this.snackBar.open(
          after.active ? `Done. Next: ${after.dueOn}.` : `Done. "${after.title}" is finished.`,
          undefined,
          { duration: 4000 },
        );
      },
      error: () => this.notice.set('Could not mark that done.'),
    });
  }

  protected remove(reminder: Reminder): void {
    this.api.deleteReminder(reminder.id).subscribe({
      next: () => this.reload(),
      error: () => this.notice.set('Could not remove that reminder.'),
    });
  }

  /** The screen a link goes to, by name, for the link's accessible label. */
  protected screenName(link: string): string {
    const names: Record<string, string> = {
      '/dashboard': 'Dashboard',
      '/import': 'Import',
      '/budget': 'Budget',
      '/markets': 'Markets',
      '/cashflow': 'Cash flow',
      '/strategies': 'Strategies',
      '/transactions': 'Transactions',
    };
    return names[link] ?? link;
  }

  /** "overdue by 3 days", "due today", "due in 12 days". Computed from the date, never guessed. */
  protected when(reminder: Reminder): string {
    const due = new Date(reminder.dueOn + 'T00:00:00');
    const now = new Date(today() + 'T00:00:00');
    const days = Math.round((due.getTime() - now.getTime()) / 86_400_000);
    if (days < 0) return `overdue by ${-days} day${days === -1 ? '' : 's'}`;
    if (days === 0) return 'due today';
    if (days === 1) return 'due tomorrow';
    return `due in ${days} days`;
  }

  protected cadenceLabel(value: ReminderCadence): string {
    return this.cadences.find((c) => c.value === value)?.label ?? value;
  }

  protected dueClass(reminder: Reminder): string {
    const due = new Date(reminder.dueOn + 'T00:00:00');
    const now = new Date(today() + 'T00:00:00');
    return due < now ? 'overdue' : '';
  }
}
