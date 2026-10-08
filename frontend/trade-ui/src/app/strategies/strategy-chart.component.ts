import { Component, DestroyRef, ElementRef, computed, inject, input, signal } from '@angular/core';

import { StrategyChart, StrategyChartMarker, StrategyChartPoint } from '../api/strategy.service';

interface Tick {
  position: number;
  label: string;
}

interface MarkerView {
  key: string;
  x: number;
  y: number;
  path: string;
  marker: StrategyChartMarker;
  kind: 'placed' | 'skipped' | 'failed';
}

type LineKey = 'shortAverage' | 'longAverage' | 'middleBand' | 'upperBand' | 'lowerBand';

const PAD = { left: 8, right: 64, top: 14, bottom: 26 };
const MIN_WIDTH = 280;
const MARKER = 7;

/**
 * Hand-drawn SVG line chart of what a strategy's rule sees: the stored prices for its symbol, the lines
 * the rule compares (both moving averages, or the Bollinger bands), and a triangle wherever the rule
 * fired: up for a buy, down for a sell; solid when an order was placed, hollow when a control stopped
 * it, red-edged when the order failed. Points are evenly spaced because the rule counts price updates,
 * not time. Hover or use the arrow keys to read a point.
 */
@Component({
  selector: 'app-strategy-chart',
  templateUrl: './strategy-chart.component.html',
  styleUrl: './strategy-chart.component.css'
})
export class StrategyChartComponent {
  readonly chart = input.required<StrategyChart>();
  readonly currencySymbol = input('');

  readonly height = 300;
  readonly width = signal(720);
  readonly hoverIndex = signal<number | null>(null);

  private readonly plotHeight = this.height - PAD.top - PAD.bottom;
  private readonly points = computed(() => this.chart().points);
  readonly isCrossover = computed(() => this.chart().type === 'MA_CROSSOVER');
  readonly plotRight = computed(() => this.width() - PAD.right);
  readonly axisLabelX = computed(() => this.width() - PAD.right + 8);
  readonly axisBottom = this.height - 8;
  readonly plotBottom = this.height - PAD.bottom;

  private readonly step = computed(() => {
    const n = this.points().length;
    return n > 1 ? (this.plotRight() - PAD.left) / (n - 1) : 0;
  });

  private readonly range = computed(() => {
    const values: number[] = [];
    for (const point of this.points()) {
      values.push(point.price);
      for (const key of ['shortAverage', 'longAverage', 'upperBand', 'lowerBand'] as const) {
        const value = point[key];
        if (value !== null) {
          values.push(value);
        }
      }
    }
    if (values.length === 0) {
      return { min: 0, max: 1 };
    }
    const low = Math.min(...values);
    const high = Math.max(...values);
    const pad = (high - low || Math.abs(high) * 0.02 || 1) * 0.12;
    return { min: low - pad, max: high + pad };
  });

  readonly pricePath = computed(() => this.path((point) => point.price));
  readonly shortPath = computed(() => this.linePath('shortAverage'));
  readonly longPath = computed(() => this.linePath('longAverage'));
  readonly middlePath = computed(() => this.linePath('middleBand'));
  readonly upperPath = computed(() => this.linePath('upperBand'));
  readonly lowerPath = computed(() => this.linePath('lowerBand'));

  // the area between the bands, over the points that have both
  readonly bandArea = computed(() => {
    const points = this.points();
    const inside = points
      .map((point, index) => ({ point, index }))
      .filter(({ point }) => point.upperBand !== null && point.lowerBand !== null);
    if (inside.length < 2) {
      return '';
    }
    const top = inside.map(({ point, index }, i) => `${i === 0 ? 'M' : 'L'}${this.x(index)},${this.y(point.upperBand!)}`);
    const bottom = [...inside].reverse().map(({ point, index }) => `L${this.x(index)},${this.y(point.lowerBand!)}`);
    return `${top.join(' ')} ${bottom.join(' ')} Z`;
  });

  readonly markers = computed<MarkerView[]>(() =>
    this.chart().markers.map((marker) => {
      const x = this.x(marker.pointIndex);
      const y = this.y(marker.quotePrice);
      // a buy points up from below the price, a sell points down from above it
      const path = marker.signal === 'BUY'
        ? `M${x},${y + 4} L${x - MARKER},${y + 4 + MARKER * 1.6} L${x + MARKER},${y + 4 + MARKER * 1.6} Z`
        : `M${x},${y - 4} L${x - MARKER},${y - 4 - MARKER * 1.6} L${x + MARKER},${y - 4 - MARKER * 1.6} Z`;
      const kind = marker.outcome === 'PLACED' ? 'placed' : marker.outcome === 'FAILED' ? 'failed' : 'skipped';
      return { key: `${marker.pointIndex}-${marker.signal}-${marker.outcome}`, x, y, path, marker, kind };
    })
  );

  readonly priceTicks = computed<Tick[]>(() => {
    const { min, max } = this.range();
    return Array.from({ length: 5 }, (_, i) => {
      const value = min + ((max - min) * i) / 4;
      return { position: this.y(value), label: this.formatPrice(value) };
    });
  });

  readonly timeTicks = computed<Tick[]>(() => {
    const points = this.points();
    const count = Math.min(5, points.length);
    if (count === 0) {
      return [];
    }
    const spansDays = points.length > 1
      && new Date(points[points.length - 1].quotedAt).toDateString() !== new Date(points[0].quotedAt).toDateString();
    return Array.from({ length: count }, (_, i) => {
      const index = count === 1 ? 0 : Math.round(((points.length - 1) * i) / (count - 1));
      return { position: this.x(index), label: this.formatTime(points[index].quotedAt, spansDays) };
    });
  });

  readonly lastPrice = computed(() => {
    const points = this.points();
    const last = points[points.length - 1];
    return last ? { y: this.y(last.price), label: this.formatPrice(last.price) } : null;
  });

  readonly hovered = computed(() => {
    const index = this.hoverIndex();
    const points = this.points();
    if (index === null || index < 0 || index >= points.length) {
      return null;
    }
    const x = this.x(index);
    return {
      index,
      point: points[index],
      x,
      y: this.y(points[index].price),
      fired: this.chart().markers.filter((marker) => marker.pointIndex === index),
      tooltipLeft: Math.min(Math.max(x + 14, 0), this.width() - 236)
    };
  });

  readonly summary = computed(() => {
    const chart = this.chart();
    const points = chart.points;
    if (points.length === 0) {
      return `No prices stored yet for ${chart.symbol}.`;
    }
    return `${points.length} prices for ${chart.symbol}, last ${this.formatPrice(points[points.length - 1].price)}; `
      + `the rule fired ${chart.markers.length} time${chart.markers.length === 1 ? '' : 's'}.`;
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
    const count = this.points().length;
    if (count === 0) {
      return;
    }
    const rect = (event.currentTarget as Element).getBoundingClientRect();
    const step = this.step();
    const index = step > 0 ? Math.round((event.clientX - rect.left - PAD.left) / step) : 0;
    this.hoverIndex.set(Math.min(Math.max(index, 0), count - 1));
  }

  onKey(event: KeyboardEvent): void {
    const last = this.points().length - 1;
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

  outcomeLabel(marker: StrategyChartMarker): string {
    switch (marker.outcome) {
      case 'PLACED':
        return 'Order placed';
      case 'FAILED':
        return 'Order failed';
      case 'SKIPPED_DISABLED':
        return 'Not placed: disabled';
      case 'SKIPPED_SPEND_LIMIT':
        return 'Not placed: spend limit';
      case 'SKIPPED_POSITION_LIMIT':
        return 'Not placed: position limit';
      case 'SKIPPED_NO_HOLDING':
        return 'Not placed: nothing to sell';
    }
  }

  formatPrice(value: number): string {
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: Math.abs(value) < 10 ? 4 : 2
    });
  }

  formatTime(iso: string, withDate = false): string {
    const date = new Date(iso);
    const time = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
    return withDate ? `${date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })} ${time}` : time;
  }

  private linePath(key: LineKey): string {
    return this.path((point) => point[key]);
  }

  // a polyline through the points that have a value, broken where a value is missing
  private path(value: (point: StrategyChartPoint) => number | null): string {
    let drawing = false;
    const parts: string[] = [];
    this.points().forEach((point, index) => {
      const v = value(point);
      if (v === null) {
        drawing = false;
        return;
      }
      parts.push(`${drawing ? 'L' : 'M'}${this.x(index)},${this.y(v)}`);
      drawing = true;
    });
    return parts.join(' ');
  }

  private x(index: number): number {
    return this.points().length > 1 ? PAD.left + this.step() * index : (PAD.left + this.plotRight()) / 2;
  }

  private y(price: number): number {
    const { min, max } = this.range();
    return PAD.top + (1 - (price - min) / (max - min)) * this.plotHeight;
  }
}
