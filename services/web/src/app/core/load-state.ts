import { signal } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Loading, error, or data — three states, distinguished.
 *
 * <p>Every screen used to hold a bare array and a `loading` flag, with the error branch of each
 * request doing nothing but clearing the flag. So a server failure rendered the *empty* state:
 * "No accounts yet. Add one" for somebody with three years of statements. For a finance app that
 * is the worst possible message, and it was reachable from four screens.
 *
 * <p>This is deliberately tiny — a value, an error, and a flag — so it can sit behind any request
 * without dragging in a framework. `run()` replaces whatever is in flight: the newest request
 * wins, and an older response that lands late is ignored.
 */
export class LoadState<T> {
  readonly value = signal<T | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  private generation = 0;

  constructor(private readonly failureMessage: string) {}

  run(source: Observable<T>, onValue?: (value: T) => void): void {
    const mine = ++this.generation;
    this.loading.set(true);
    this.error.set(null);
    source.subscribe({
      next: (value) => {
        if (mine !== this.generation) return;
        this.value.set(value);
        this.loading.set(false);
        onValue?.(value);
      },
      error: () => {
        if (mine !== this.generation) return;
        this.loading.set(false);
        this.error.set(this.failureMessage);
      },
    });
  }

  /** True only when a load has finished with nothing in it — never while loading or after a failure. */
  isEmpty(): boolean {
    const value = this.value();
    return !this.loading() && this.error() === null && Array.isArray(value) && value.length === 0;
  }
}
