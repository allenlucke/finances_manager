import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { money } from '../../core/money';
import { SystemStatus } from '../../core/models';

/**
 * Whether the machinery behind the screens is alive, in words. The same facts `make doctor`
 * prints on the box, for the person looking at it from a phone.
 */
@Component({
  selector: 'app-system-status',
  imports: [DatePipe, MatCardModule, MatButtonModule, MatIconModule],
  templateUrl: './system-status.html',
  styleUrl: './system-status.scss',
})
export class SystemStatusComponent {
  private readonly api = inject(ApiClient);
  protected readonly status = new LoadState<SystemStatus>('Could not ask the API how it is doing.');
  protected readonly money = money;

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.status.run(this.api.systemStatus());
  }

  /** Each row is a fact and a verdict: ok, off, or attention. */
  protected rows(s: SystemStatus): { label: string; text: string; state: 'ok' | 'off' | 'warn' }[] {
    const rows: { label: string; text: string; state: 'ok' | 'off' | 'warn' }[] = [];
    rows.push({
      label: 'AI service',
      text:
        s.aiService === 'ok'
          ? 'answering'
          : `${s.aiService} — imports and categorization will fail`,
      state: s.aiService === 'ok' ? 'ok' : 'warn',
    });
    rows.push({
      label: 'Market data',
      text: s.marketData.available
        ? `${s.marketData.provider}${s.marketScheduled ? `, polling every ${s.marketRefreshEvery}` : ', not polling on a timer'}`
        : `off (${s.marketData.detail ?? 'no provider'})`,
      state: s.marketData.available ? (s.marketScheduled ? 'ok' : 'warn') : 'off',
    });
    rows.push({
      label: 'Quotes last fetched',
      text: s.lastMarketRefresh
        ? `${s.lastMarketRefreshOutcome ?? ''}`
        : 'never since the API started',
      state: s.lastMarketRefresh ? 'ok' : s.marketData.available ? 'warn' : 'off',
    });
    rows.push({
      label: 'Broker',
      text: s.broker.available
        ? `${s.broker.broker}${s.broker.paper ? ' (paper)' : ' — NOT PAPER'}, trading ${s.tradingEnabled ? 'on' : 'off'}, cap ${money(s.tradingDailyCap)} a day`
        : `none (${s.broker.detail ?? 'TRADING_BROKER is none'}); trading ${s.tradingEnabled ? 'on' : 'off'}`,
      state: s.broker.available && !s.broker.paper ? 'warn' : s.broker.available ? 'ok' : 'off',
    });
    rows.push({
      label: 'Daily digest',
      text: s.digestScheduled
        ? s.lastDigest
          ? `last run ${s.lastDigest.forDate}, ${s.lastDigest.items} item(s), ${s.lastDigest.sent ? 'delivered' : `not delivered: ${s.lastDigest.deliveryError}`}`
          : 'scheduled, has not run yet'
        : 'not scheduled here',
      state: !s.digestScheduled ? 'off' : s.lastDigest && !s.lastDigest.sent ? 'warn' : 'ok',
    });
    rows.push({
      label: 'Notifications',
      text: s.notifierConfigured
        ? 'ntfy configured'
        : 'no channel (NTFY_URL is blank): alerts and the digest are recorded, not pushed',
      state: s.notifierConfigured ? 'ok' : 'warn',
    });
    rows.push({
      label: 'Net worth snapshot',
      text: s.lastSnapshot
        ? `last taken ${s.lastSnapshot}`
        : 'none yet; the first is taken by the housekeeping tick',
      state: s.lastSnapshot ? 'ok' : 'warn',
    });
    rows.push({
      label: 'Strategies',
      text: s.activeStrategies
        ? `${s.activeStrategies} live, asked every ${s.strategyEvaluateEvery}${s.marketScheduled ? '' : ' (but the market timer is off)'}`
        : 'none live',
      state: s.activeStrategies && !s.marketScheduled ? 'warn' : s.activeStrategies ? 'ok' : 'off',
    });
    return rows;
  }
}
