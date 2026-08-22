import { Component, inject, signal } from '@angular/core';
import { ApiClient, SystemStatus } from '../../core/api';

type LoadState = 'loading' | 'loaded' | 'error';

/**
 * M0 scaffold view: proves the browser can reach the API, and the API can reach both
 * PostgreSQL and the Python AI service. Delete once M1 has real screens.
 */
@Component({
  selector: 'app-status',
  templateUrl: './status.html',
  styleUrl: './status.scss',
})
export class StatusComponent {
  private readonly api = inject(ApiClient);

  protected readonly state = signal<LoadState>('loading');
  protected readonly status = signal<SystemStatus | null>(null);

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.state.set('loading');
    this.api.status().subscribe({
      next: (status) => {
        this.status.set(status);
        this.state.set('loaded');
      },
      error: () => {
        this.status.set(null);
        this.state.set('error');
      },
    });
  }

  protected healthy(value: string | undefined): boolean {
    return !!value && !value.includes('unreachable') && value !== 'unknown';
  }
}
