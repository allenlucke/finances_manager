import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiClient, SystemStatus } from './api';

describe('ApiClient', () => {
  let api: ApiClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests status from the versioned API path', () => {
    const expected: SystemStatus = {
      service: 'finances-api',
      version: '0.1.0-SNAPSHOT',
      time: '2026-08-22T00:00:00Z',
      database: 'up (PostgreSQL 18.0)',
      aiService: 'ok',
    };

    let actual: SystemStatus | undefined;
    api.status().subscribe((status) => (actual = status));

    const request = httpMock.expectOne('/api/v1/status');
    expect(request.request.method).toBe('GET');
    request.flush(expected);

    expect(actual).toEqual(expected);
  });
});
