import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { NotificationSettingsComponent } from './notification-settings';

/** The settings arrive before the form claims anything, save as numbers, and a missing channel is said. */
describe('NotificationSettingsComponent', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<NotificationSettingsComponent>>;
  let component: Record<string, any>;

  const text = () =>
    ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
  const settings = {
    digestEnabled: true,
    digestTime: '07:30:00',
    staleAfterDays: 35,
    draftWaitHours: 24,
    quietWhenEmpty: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    fixture = TestBed.createComponent(NotificationSettingsComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('fills the form from the saved settings and saves them back as numbers', () => {
    backend.expectOne('/api/v1/digest/settings').flush(settings);
    fixture.detectChanges();

    expect(component['form'].getRawValue()).toEqual({
      digestEnabled: true,
      digestTime: '07:30',
      staleAfterDays: '35',
      draftWaitHours: '24',
      quietWhenEmpty: true,
    });
    component['form'].patchValue({ digestTime: '06:15', staleAfterDays: '45' });
    component['save']();

    const request = backend.expectOne('/api/v1/digest/settings');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      digestEnabled: true,
      digestTime: '06:15',
      staleAfterDays: 45,
      draftWaitHours: 24,
      quietWhenEmpty: true,
    });
    request.flush({ ...settings, digestTime: '06:15:00', staleAfterDays: 45 });
  });

  it('a test send with no channel says so, in the channel’s words', () => {
    backend.expectOne('/api/v1/digest/settings').flush(settings);
    fixture.detectChanges();

    component['sendTest']();
    backend.expectOne('/api/v1/digest/send').flush({
      id: 1,
      forDate: '2026-09-26',
      ranAt: '2026-09-26T12:00:00Z',
      manual: true,
      items: 0,
      sent: false,
      deliveryError: 'No notification channel is configured (NTFY_URL is blank).',
      body: 'All quiet: nothing needs a look.',
    });
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent,
    ).toContain('NTFY_URL');
  });

  it('a failed load is a sentence, not a form of defaults', () => {
    backend.expectOne('/api/v1/digest/settings').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load the notification settings');
    expect((fixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();
  });
});
