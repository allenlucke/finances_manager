import { Subject, of, throwError } from 'rxjs';
import { LoadState } from './load-state';

describe('LoadState', () => {
  it('starts with nothing loaded and no error', () => {
    const state = new LoadState<number[]>('failed');
    expect(state.value()).toBeNull();
    expect(state.error()).toBeNull();
    expect(state.loading()).toBe(false);
    expect(state.isEmpty()).toBe(false);
  });

  it('holds the value when the request succeeds', () => {
    const state = new LoadState<number[]>('failed');
    state.run(of([1, 2]));
    expect(state.value()).toEqual([1, 2]);
    expect(state.loading()).toBe(false);
    expect(state.isEmpty()).toBe(false);
  });

  it('is empty only after a successful load of nothing', () => {
    const state = new LoadState<number[]>('failed');
    state.run(of([]));
    expect(state.isEmpty()).toBe(true);
  });

  it('a failure is an error, never an empty state', () => {
    // Four screens used to render "nothing here yet" on a 500. For someone with three years of
    // statements that is the worst possible message, and it is the whole reason this class exists.
    const state = new LoadState<number[]>('Could not load accounts.');
    state.run(throwError(() => new Error('boom')));
    expect(state.error()).toBe('Could not load accounts.');
    expect(state.isEmpty()).toBe(false);
    expect(state.loading()).toBe(false);
  });

  it('the newest request wins, and a late older response is ignored', () => {
    const state = new LoadState<string>('failed');
    const first = new Subject<string>();
    const second = new Subject<string>();
    state.run(first);
    state.run(second);
    second.next('new');
    first.next('old');
    expect(state.value()).toBe('new');
  });
});
