import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('creates the root component', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('primes CSRF on startup, before anything tries to write', () => {
    TestBed.createComponent(App);
    const httpMock = TestBed.inject(HttpTestingController);

    // Without this the very first POST — including login — would be rejected for want of a token.
    httpMock.expectOne('/api/v1/auth/csrf');
  });
});
