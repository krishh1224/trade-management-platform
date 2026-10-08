import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StrategyChart } from '../api/strategy.service';
import { StrategyChartComponent } from './strategy-chart.component';

function crossover(): StrategyChart {
  const prices = [1500, 1490, 1480, 1470, 1530];
  const shortAverage = [null, 1495, 1485, 1475, 1500];
  const longAverage = [null, null, null, 1485, 1492.5];
  return {
    strategyId: 7,
    symbol: 'INFY.NS',
    type: 'MA_CROSSOVER',
    requiredPrices: 5,
    points: prices.map((price, i) => ({
      quotedAt: `2026-10-08T09:0${i}:00Z`,
      price,
      shortAverage: shortAverage[i],
      longAverage: longAverage[i],
      middleBand: null,
      upperBand: null,
      lowerBand: null
    })),
    markers: [
      { pointIndex: 4, quotedAt: '2026-10-08T09:04:00Z', quotePrice: 1530, signal: 'BUY', outcome: 'PLACED', orderId: 'ORD-abc', detail: null },
      { pointIndex: 2, quotedAt: '2026-10-08T09:02:00Z', quotePrice: 1480, signal: 'SELL', outcome: 'SKIPPED_NO_HOLDING', orderId: null, detail: null }
    ]
  };
}

function bands(): StrategyChart {
  const chart = crossover();
  return {
    ...chart,
    type: 'BOLLINGER_BANDS',
    markers: [],
    points: chart.points.map((point, i) => ({
      ...point,
      shortAverage: null,
      longAverage: null,
      middleBand: i >= 3 ? 1490 : null,
      upperBand: i >= 3 ? 1500 : null,
      lowerBand: i >= 3 ? 1480 : null
    }))
  };
}

describe('StrategyChartComponent', () => {
  let fixture: ComponentFixture<StrategyChartComponent>;
  let component: StrategyChartComponent;

  function render(chart: StrategyChart): HTMLElement {
    fixture.componentRef.setInput('chart', chart);
    fixture.componentRef.setInput('currencySymbol', '₹');
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [StrategyChartComponent] }).compileComponents();
    fixture = TestBed.createComponent(StrategyChartComponent);
    component = fixture.componentInstance;
  });

  it('draws the price and both averages, each line starting where its window fits', () => {
    const page = render(crossover());

    expect(page.querySelector('[data-testid="price-line"]')?.getAttribute('d')?.match(/[ML]/g)).toHaveLength(5);
    expect(page.querySelector('[data-testid="short-average"]')?.getAttribute('d')?.match(/[ML]/g)).toHaveLength(4);
    expect(page.querySelector('[data-testid="long-average"]')?.getAttribute('d')?.match(/[ML]/g)).toHaveLength(2);
    expect(page.querySelector('[data-testid="band-area"]')).toBeNull();
  });

  it('marks a placed buy solid and a skipped sell hollow', () => {
    const page = render(crossover());

    const placed = page.querySelector('.marker[data-outcome="PLACED"]');
    const skipped = page.querySelector('.marker[data-outcome="SKIPPED_NO_HOLDING"]');
    expect(placed?.classList).toContain('buy');
    expect(placed?.classList).toContain('placed');
    expect(skipped?.classList).toContain('sell');
    expect(skipped?.classList).toContain('skipped');
  });

  it('draws the bands and the area between them for a Bollinger strategy', () => {
    const page = render(bands());

    expect(page.querySelector('[data-testid="band-area"]')?.getAttribute('d')).toContain('Z');
    expect(page.querySelector('[data-testid="middle-band"]')?.getAttribute('d')?.match(/[ML]/g)).toHaveLength(2);
    expect(page.querySelector('[data-testid="short-average"]')).toBeNull();
  });

  it('reads a point and what the rule did there with the keyboard', () => {
    const page = render(crossover());

    component.onKey(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    fixture.detectChanges();

    const tooltip = page.querySelector('[data-testid="strategy-chart-tooltip"]')!.textContent!;
    expect(tooltip).toContain('₹1,530.00');
    expect(tooltip).toContain('1,500.00');
    expect(tooltip).toContain('1,492.50');
    expect(tooltip).toContain('BUY · Order placed');
    expect(tooltip).toContain('ORD-abc');
  });

  it('describes itself for screen readers', () => {
    const page = render(crossover());

    expect(page.querySelector('svg')?.getAttribute('aria-label')).toBe('5 prices for INFY.NS, last 1,530.00; the rule fired 2 times.');
  });
});
