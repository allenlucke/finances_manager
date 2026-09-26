import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { money } from '../../core/money';
import {
  OrderEvent,
  OrderSide,
  OrderType,
  OrderVenue,
  TradeOrder,
  TradingStatus,
} from '../../core/models';

type Detail = { status?: number; error?: { detail?: string | null } | null };

/**
 * Orders (M7b, D-18): propose, confirm by restating, execute within a cap — or carry a ticket to
 * Fidelity by hand and mark it placed.
 *
 * <p>The confirmation here is typing the symbol back: the person reads the sentence that says what
 * is about to be sent and restates the one word that identifies it. The server then checks the
 * echo, the kill switch and the daily cap in that order, and every refusal arrives as a sentence
 * that is shown, not swallowed. Nothing on this card is a ledger row.
 */
@Component({
  selector: 'app-orders-card',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './orders-card.html',
  styleUrl: './orders-card.scss',
})
export class OrdersCardComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly status = new LoadState<TradingStatus>('Could not check the trading status.');
  protected readonly orders = new LoadState<TradeOrder[]>('Could not load orders.');
  protected readonly events = new LoadState<OrderEvent[]>('Could not load the order history.');
  protected readonly busy = signal(false);
  protected readonly syncing = signal(false);
  /** The last refusal, kept on screen until the next action succeeds. */
  protected readonly notice = signal<string | null>(null);
  protected readonly confirming = signal<TradeOrder | null>(null);
  protected readonly placing = signal<TradeOrder | null>(null);
  protected readonly historyFor = signal<TradeOrder | null>(null);

  protected readonly money = money;
  protected readonly columns = ['when', 'order', 'notional', 'status', 'actions'];

  protected readonly proposeForm = this.forms.nonNullable.group({
    symbol: ['', [Validators.required, Validators.pattern(/^[A-Za-z][A-Za-z0-9.\-]{0,15}$/)]],
    side: ['buy' as OrderSide, Validators.required],
    quantity: ['', [Validators.required, Validators.pattern(/^(?!0+(\.0+)?$)\d+(\.\d{1,8})?$/)]],
    orderType: ['market' as OrderType, Validators.required],
    limitPrice: ['', Validators.pattern(/^\d+(\.\d{1,6})?$/)],
    venue: ['paper' as OrderVenue, Validators.required],
    rationale: [''],
  });
  /** What the person types to confirm: the symbol, restated. */
  protected readonly confirmForm = this.forms.nonNullable.group({ echo: [''] });
  protected readonly placedForm = this.forms.nonNullable.group({
    fillPrice: ['', Validators.pattern(/^\d+(\.\d{1,6})?$/)],
  });
  protected get echo() {
    return this.confirmForm.controls.echo;
  }
  protected get fillPrice() {
    return this.placedForm.controls.fillPrice;
  }

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.status.run(this.api.tradingStatus());
    this.orders.run(this.api.recentOrders(50));
  }

  protected proposeDisabled(): boolean {
    const value = this.proposeForm.getRawValue();
    return (
      this.proposeForm.invalid || this.busy() || (value.orderType === 'limit' && !value.limitPrice)
    );
  }

  protected propose(): void {
    if (this.proposeDisabled()) return;
    const value = this.proposeForm.getRawValue();
    this.busy.set(true);
    this.api
      .proposeOrder({
        symbol: value.symbol.toUpperCase(),
        venue: value.venue,
        side: value.side,
        quantity: value.quantity,
        orderType: value.orderType,
        limitPrice: value.orderType === 'limit' ? value.limitPrice : null,
        timeInForce: 'day',
        proposedBy: 'person',
        rationale: value.rationale || null,
      })
      .subscribe({
        next: (order) => {
          this.busy.set(false);
          this.notice.set(null);
          this.proposeForm.reset({
            symbol: '',
            side: value.side,
            quantity: '',
            orderType: value.orderType,
            limitPrice: '',
            venue: value.venue,
            rationale: '',
          });
          this.reload();
          this.snackBar.open(
            `Draft saved: ${order.description}. Nothing is sent until you confirm.`,
            undefined,
            {
              duration: 6000,
            },
          );
        },
        error: (error: Detail) => {
          this.busy.set(false);
          this.notice.set(error?.error?.detail || 'Could not save the draft.');
        },
      });
  }

  protected beginConfirm(order: TradeOrder): void {
    this.placing.set(null);
    this.echo.setValue('');
    this.confirming.set(order);
  }

  protected echoMatches(order: TradeOrder): boolean {
    return this.echo.value.trim().toUpperCase() === order.symbol;
  }

  protected sendConfirm(): void {
    const order = this.confirming();
    if (!order || !this.echoMatches(order) || this.busy()) return;
    this.busy.set(true);
    this.api
      .confirmOrder(order.id, {
        symbol: this.echo.value.trim().toUpperCase(),
        side: order.side,
        quantity: String(order.quantity),
        limitPrice: order.limitPrice === null ? null : String(order.limitPrice),
      })
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.confirming.set(null);
          this.notice.set(null);
          this.reload();
          this.snackBar.open(
            result.venue === 'manual'
              ? `Confirmed. Place ${result.description} at Fidelity, then mark it placed here.`
              : `${result.description}: ${this.statusLabel(result)}.`,
            undefined,
            { duration: 7000 },
          );
        },
        error: (error: Detail) => {
          this.busy.set(false);
          this.confirming.set(null);
          this.notice.set(error?.error?.detail || 'The order could not be confirmed.');
          this.reload();
        },
      });
  }

  protected cancel(order: TradeOrder): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.api.cancelOrder(order.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set(null);
        this.reload();
      },
      error: (error: Detail) => {
        this.busy.set(false);
        this.notice.set(error?.error?.detail || 'Could not cancel that order.');
      },
    });
  }

  protected beginPlaced(order: TradeOrder): void {
    this.confirming.set(null);
    this.fillPrice.setValue('');
    this.placing.set(order);
  }

  protected markPlaced(): void {
    const order = this.placing();
    if (!order || this.fillPrice.invalid || this.busy()) return;
    this.busy.set(true);
    this.api.markOrderPlaced(order.id, this.fillPrice.value || null).subscribe({
      next: () => {
        this.busy.set(false);
        this.placing.set(null);
        this.notice.set(null);
        this.reload();
      },
      error: (error: Detail) => {
        this.busy.set(false);
        this.notice.set(error?.error?.detail || 'Could not mark that order placed.');
      },
    });
  }

  protected sync(): void {
    if (this.syncing()) return;
    this.syncing.set(true);
    this.api.syncOrders().subscribe({
      next: (result) => {
        this.syncing.set(false);
        this.reload();
        this.snackBar.open(
          result.changed === 0 ? 'No open order changed.' : `${result.changed} order(s) changed.`,
          undefined,
          { duration: 4000 },
        );
      },
      error: (error: Detail) => {
        this.syncing.set(false);
        this.notice.set(error?.error?.detail || 'Could not ask the broker.');
      },
    });
  }

  protected showHistory(order: TradeOrder): void {
    if (this.historyFor()?.id === order.id) {
      this.historyFor.set(null);
      return;
    }
    this.historyFor.set(order);
    this.events.run(this.api.orderEvents(order.id));
  }

  protected canConfirm(order: TradeOrder): boolean {
    return order.status === 'draft' || (order.status === 'confirmed' && order.venue === 'paper');
  }

  protected canCancel(order: TradeOrder): boolean {
    return ['draft', 'confirmed', 'submitted', 'accepted', 'partially_filled'].includes(
      order.status,
    );
  }

  protected canPlace(order: TradeOrder): boolean {
    return order.status === 'confirmed' && order.venue === 'manual';
  }

  /** The state in the person's words, with the fill where there is one. */
  protected statusLabel(order: TradeOrder): string {
    switch (order.status) {
      case 'draft':
        return 'draft';
      case 'confirmed':
        return order.venue === 'manual' ? 'confirmed — take it to Fidelity' : 'confirmed, not sent';
      case 'submitted':
        return 'sent to the broker';
      case 'accepted':
        return 'accepted by the broker';
      case 'partially_filled':
        return `partly filled: ${order.filledQuantity} of ${order.quantity}`;
      case 'filled':
        return order.filledAvgPrice === null
          ? 'filled'
          : `filled at ${money(order.filledAvgPrice)}`;
      case 'placed_manually':
        return order.filledAvgPrice === null
          ? 'placed at Fidelity'
          : `placed at Fidelity at ${money(order.filledAvgPrice)}`;
      default:
        return order.status;
    }
  }

  protected isTerminal(order: TradeOrder): boolean {
    return ['filled', 'placed_manually', 'cancelled', 'rejected', 'expired', 'failed'].includes(
      order.status,
    );
  }
}
