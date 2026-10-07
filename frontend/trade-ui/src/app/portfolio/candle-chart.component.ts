import { Component, DestroyRef, ElementRef, computed, inject, input, signal } from '@angular/core';

import { Candle } from '../api/portfolio.service';

interface CandleView {
  date: string;
  wickX: number;
  wickTop: number;
  wickBottom: number;
  bodyX: number;
  bodyY: number;
  bodyWidth: number;
  bodyHeight: number;
  volumeY: number;
  volumeHeight: number;
  direction: 'up' | 'down' | 'flat';
  synthetic: boolean;
}

interface Tick {
  position: number;
  label: string;
}

const PAD = { left: 8, right: 64, top: 12, bottom: 26 };
const VOLUME_HEIGHT = 56;
const GAP = 12;
const MIN_WIDTH = 280;

/**
 * Hand-drawn SVG candlestick chart with a volume strip. Green candles closed at or above their open,
 * red below; a dashed outline marks bars the provider estimated rather than observed. Hover or use the
 * arrow keys to read a bar.
 */
@Component({
  selector: 'app-candle-chart',
  templateUrl: './candle-chart.component.html',
  styleUrl: './candle-chart.component.css'
})
export class CandleChartComponent {
  readonly candles = input.required<Candle[]>();
  readonly currency = input('');

  readonly height = 360;
  readonly width = signal(720);
  readonly hoverIndex = signal<number | null>(null);

  private readonly priceHeight = this.height - PAD.top - PAD.bottom - VOLUME_HEIGHT - GAP;
  private readonly plotWidth = computed(() => this.width() - PAD.left - PAD.right);
  private readonly step = computed(() => this.plotWidth() / Math.max(this.candles().length, 1));

  private readonly priceRange = computed(() => {
    const bars = this.candles();
    const low = Math.min(...bars.map((bar) => bar.low));
    const high = Math.max(...bars.map((bar) => bar.high));
    const pad = (high - low || high * 0.02) * 0.06;
    return { min: low - pad, max: high + pad };
  });

  private readonly maxVolume = computed(() => Math.max(...this.candles().map((bar) => bar.volume), 1));

  readonly views = computed<CandleView[]>(() => {
    const step = this.step();
    const bodyWidth = Math.max(1.5, Math.min(14, step * 0.62));
    const volumeTop = PAD.top + this.priceHeight + GAP;
    return this.candles().map((bar, index) => {
      const x = PAD.left + step * (index + 0.5);
      const top = this.y(Math.max(bar.open, bar.close));
      const bottom = this.y(Math.min(bar.open, bar.close));
      const volumeHeight = (bar.volume / this.maxVolume()) * VOLUME_HEIGHT;
      return {
        date: bar.date,
        wickX: x,
        wickTop: this.y(bar.high),
        wickBottom: this.y(bar.low),
        bodyX: x - bodyWidth / 2,
        bodyY: top,
        bodyWidth,
        bodyHeight: Math.max(1, bottom - top),
        volumeY: volumeTop + VOLUME_HEIGHT - volumeHeight,
        volumeHeight,
        direction: bar.close > bar.open ? 'up' : bar.close < bar.open ? 'down' : 'flat',
        synthetic: bar.synthetic
      };
    });
  });

  readonly priceTicks = computed<Tick[]>(() => {
    const { min, max } = this.priceRange();
    // an axis label under the last-close tag would be unreadable, so that one is left out
    const lastY = this.lastClose()?.y ?? null;
    return Array.from({ length: 5 }, (_, i) => {
      const value = min + ((max - min) * i) / 4;
      return { position: this.y(value), label: this.formatPrice(value) };
    }).filter((tick) => lastY === null || Math.abs(tick.position - lastY) > 13);
  });

  readonly dateTicks = computed<Tick[]>(() => {
    const bars = this.candles();
    const count = Math.min(5, bars.length);
    if (count === 0) {
      return [];
    }
    const longSpan = bars.length > 200;
    return Array.from({ length: count }, (_, i) => {
      const index = count === 1 ? 0 : Math.round(((bars.length - 1) * i) / (count - 1));
      return { position: PAD.left + this.step() * (index + 0.5), label: this.formatDate(bars[index].date, longSpan) };
    });
  });

  readonly lastClose = computed(() => {
    const bars = this.candles();
    const last = bars[bars.length - 1];
    return last ? { y: this.y(last.close), label: this.formatPrice(last.close), up: last.close >= last.open } : null;
  });

  readonly hasSynthetic = computed(() => this.candles().some((bar) => bar.synthetic));
  readonly plotRight = computed(() => this.width() - PAD.right);
  readonly volumeBottom = PAD.top + this.priceHeight + GAP + VOLUME_HEIGHT;
  readonly axisBottom = this.height - 8;
  readonly axisLabelX = computed(() => this.width() - PAD.right + 8);

  readonly hovered = computed(() => {
    const index = this.hoverIndex();
    const bars = this.candles();
    if (index === null || index < 0 || index >= bars.length) {
      return null;
    }
    const bar = bars[index];
    const reference = index > 0 ? bars[index - 1].close : bar.open;
    return {
      index,
      bar,
      x: PAD.left + this.step() * (index + 0.5),
      change: ((bar.close - reference) / reference) * 100,
      tooltipLeft: Math.min(Math.max(PAD.left + this.step() * (index + 0.5) + 14, 0), this.width() - 196)
    };
  });

  readonly summary = computed(() => {
    const bars = this.candles();
    if (bars.length === 0) {
      return 'No candles';
    }
    const last = bars[bars.length - 1];
    return `${bars.length} daily candles from ${bars[0].date} to ${last.date}. Last close ${this.formatPrice(last.close)} ${this.currency()}.`;
  });

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver((entries) => {
        const next = Math.floor(entries[0]?.contentRect.width ?? 0);
        if (next > 0) {
          this.width.set(Math.max(MIN_WIDTH, next));
        }
      });
      observer.observe(host);
      inject(DestroyRef).onDestroy(() => observer.disconnect());
    }
  }

  onMove(event: MouseEvent): void {
    const rect = (event.currentTarget as Element).getBoundingClientRect();
    const index = Math.floor((event.clientX - rect.left - PAD.left) / this.step());
    this.hoverIndex.set(Math.min(Math.max(index, 0), this.candles().length - 1));
  }

  onKey(event: KeyboardEvent): void {
    const last = this.candles().length - 1;
    const current = this.hoverIndex();
    if (event.key === 'ArrowLeft') {
      this.hoverIndex.set(current === null ? last : Math.max(current - 1, 0));
    } else if (event.key === 'ArrowRight') {
      this.hoverIndex.set(current === null ? last : Math.min(current + 1, last));
    } else if (event.key === 'Escape') {
      this.hoverIndex.set(null);
    } else {
      return;
    }
    event.preventDefault();
  }

  formatPrice(value: number): string {
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: Math.abs(value) < 10 ? 4 : 2
    });
  }

  formatVolume(value: number): string {
    if (value >= 1_000_000) {
      return `${(value / 1_000_000).toFixed(2)}M`;
    }
    return value >= 1_000 ? `${(value / 1_000).toFixed(1)}K` : String(value);
  }

  formatDate(iso: string, withYear = false): string {
    const date = new Date(`${iso}T00:00:00`);
    return date.toLocaleDateString('en-GB', withYear ? { month: 'short', year: '2-digit' } : { day: 'numeric', month: 'short' });
  }

  private y(price: number): number {
    const { min, max } = this.priceRange();
    return PAD.top + (1 - (price - min) / (max - min)) * this.priceHeight;
  }
}
