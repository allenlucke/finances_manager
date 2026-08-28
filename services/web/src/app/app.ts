import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Auth } from './core/auth';

/**
 * Application shell: the toolbar and navigation that persist across routes.
 *
 * <p>Renders nothing but a progress bar while auth state is `unknown`. Showing the chrome first and
 * filling it in afterwards would flash a signed-in-looking layout at someone who is not signed in.
 */
@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly auth = inject(Auth);

  protected readonly state = this.auth.state;
  protected readonly user = this.auth.user;
  protected readonly showChrome = computed(() => this.auth.state() === 'signed-in');

  constructor() {
    this.auth.bootstrap();
  }

  protected logout(): void {
    this.auth.logout();
  }
}
