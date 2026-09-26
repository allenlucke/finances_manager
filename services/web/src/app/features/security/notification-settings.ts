import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { DigestSettings } from '../../core/models';

type Detail = { error?: { detail?: string | null } | null };

/** When the daily digest goes out and what counts as stale (M8, D-20). One row per person. */
@Component({
  selector: 'app-notification-settings',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    MatButtonModule,
  ],
  templateUrl: './notification-settings.html',
  styleUrl: './notification-settings.scss',
})
export class NotificationSettingsComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly settings = new LoadState<DigestSettings>(
    'Could not load the notification settings.',
  );
  protected readonly saving = signal(false);
  protected readonly sending = signal(false);
  protected readonly notice = signal<string | null>(null);

  protected readonly form = this.forms.nonNullable.group({
    digestEnabled: [true],
    digestTime: ['07:30', [Validators.required, Validators.pattern(/^([01]\d|2[0-3]):[0-5]\d$/)]],
    staleAfterDays: ['35', [Validators.required, Validators.pattern(/^\d{1,3}$/)]],
    draftWaitHours: ['24', [Validators.required, Validators.pattern(/^\d{1,3}$/)]],
    quietWhenEmpty: [true],
  });

  constructor() {
    this.settings.run(this.api.digestSettings(), (s) =>
      this.form.reset({
        digestEnabled: s.digestEnabled,
        digestTime: (s.digestTime ?? '07:30').slice(0, 5),
        staleAfterDays: String(s.staleAfterDays),
        draftWaitHours: String(s.draftWaitHours),
        quietWhenEmpty: s.quietWhenEmpty,
      }),
    );
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) return;
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .saveDigestSettings({
        digestEnabled: v.digestEnabled,
        digestTime: v.digestTime,
        staleAfterDays: Number(v.staleAfterDays),
        draftWaitHours: Number(v.draftWaitHours),
        quietWhenEmpty: v.quietWhenEmpty,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notice.set(null);
          this.snackBar.open('Saved.', undefined, { duration: 3000 });
        },
        error: (error: Detail) => {
          this.saving.set(false);
          this.notice.set(error?.error?.detail || 'Could not save the settings.');
        },
      });
  }

  /** The digest, now, as a test of the channel. It says in words when there is no channel. */
  protected sendTest(): void {
    if (this.sending()) return;
    this.sending.set(true);
    this.api.sendDigest().subscribe({
      next: (run) => {
        this.sending.set(false);
        this.notice.set(run.sent ? null : `Not sent: ${run.deliveryError}`);
        if (run.sent) {
          this.snackBar.open(`Sent ${run.items} item(s) to your phone.`, undefined, {
            duration: 5000,
          });
        }
      },
      error: (error: Detail) => {
        this.sending.set(false);
        this.notice.set(error?.error?.detail || 'Could not send the digest.');
      },
    });
  }
}
