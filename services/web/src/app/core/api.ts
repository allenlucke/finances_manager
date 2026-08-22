import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Shape returned by GET /api/v1/status on the Spring Boot service.
 * Scaffold-only — replaced by real resources in M1 (see docs/ROADMAP.md).
 */
export interface SystemStatus {
  service: string;
  version: string;
  time: string;
  database: string;
  aiService: string;
}

/**
 * The single place the app talks to the backend.
 *
 * Everything goes through the Java API — the browser never calls the Python AI service
 * directly. See docs/ARCHITECTURE.md.
 */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1';

  status(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>(`${this.baseUrl}/status`);
  }
}
