import { TestBed } from '@angular/core/testing';

import { Candle } from '../api/portfolio.service';
import { CandleChartComponent } from './candle-chart.component';

describe('CandleChartComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CandleChartComponent] }).compileComponents();
  });

  function render(candles: Candle[], currency = 'INR') {
    const fixture = TestBed.createComponent(CandleChartComponent);
    fixture.componentRef.setInput('candles', candles);
    fixture.componentRef.setInput('currency', currency);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    return { fixture, host, svg: host.querySelector('svg') as SVGElement };
  }

  it('draws one candle per bar, green when it closed higher and red when it closed lower', () => {
    const { host } = render([
      bar('2026-10-01', { open: 100, close: 105 }),
      bar('2026-10-02', { open: 105, close: 101 }),
      bar('2026-10-05', { open: 101, close: 101 })
    ]);

    const candles = host.querySelectorAll('g.candle');
    expect(candles).toHaveLength(3);
    expect(candles[0].classList.contains('up')).toBe(true);
    expect(candles[1].classList.contains('down')).toBe(true);
    expect(candles[2].classList.contains('flat')).toBe(true);
    expect(host.querySelectorAll('rect.volume')).toHaveLength(3);
  });

  it('puts a taller price on a higher position and keeps every wick inside the plot', () => {
    const { host } = render([bar('2026-10-01', { high: 120, low: 90 }), bar('2026-10-02', { high: 110, low: 95 })]);

    const wicks = Array.from(host.querySelectorAll('g.candle line.wick')) as SVGLineElement[];
    const top = Number(wicks[0].getAttribute('y1'));
    const bottom = Number(wicks[0].getAttribute('y2'));
    expect(top).toBeLessThan(bottom);
    expect(top).toBeGreaterThanOrEqual(0);
    expect(bottom).toBeLessThanOrEqual(360);
    // the 120 high of the first bar is above the 110 high of the second
    expect(top).toBeLessThan(Number(wicks[1].getAttribute('y1')));
  });

  it('marks estimated bars and explains them in the legend', () => {
    const { host } = render([bar('2026-10-01', {}), bar('2026-10-02', { synthetic: true })]);

    expect(host.querySelectorAll('g.candle.synthetic')).toHaveLength(1);
    expect(host.querySelector('.legend')?.textContent).toContain('Estimated by provider');
  });

  it('omits the estimated entry from the legend when every bar was observed', () => {
    const { host } = render([bar('2026-10-01', {}), bar('2026-10-02', {})]);

    expect(host.querySelector('.legend')?.textContent).not.toContain('Estimated');
  });

  it('labels the last close on the price axis', () => {
    const { host } = render([bar('2026-10-01', { close: 100 }), bar('2026-10-02', { close: 1013.85 })]);

    expect(host.querySelector('.last-label')?.textContent).toBe('1,013.85');
  });

  it('describes the chart for assistive technology', () => {
    const { svg } = render([bar('2026-10-01', { close: 100 }), bar('2026-10-02', { close: 1013.85 })], 'INR');

    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.getAttribute('aria-label')).toBe('2 daily candles from 2026-10-01 to 2026-10-02. Last close 1,013.85 INR.');
  });

  it('shows the open, high, low, close and volume of the bar under the pointer', () => {
    const { fixture, host, svg } = render([
      bar('2026-10-01', { open: 100, high: 110, low: 95, close: 105, volume: 1_500_000 }),
      bar('2026-10-02', { open: 105, high: 108, low: 99, close: 102, volume: 800 })
    ]);
    expect(host.querySelector('[data-testid="candle-tooltip"]')).toBeNull();

    // jsdom has no layout, so the chart's left edge is 0 and clientX is already the chart x
    svg.dispatchEvent(new MouseEvent('pointermove', { clientX: 20, bubbles: true }));
    fixture.detectChanges();

    const tooltip = host.querySelector('[data-testid="candle-tooltip"]') as HTMLElement;
    expect(tooltip.textContent).toContain('1 Oct 2026');
    expect(tooltip.textContent).toContain('110.00');
    expect(tooltip.textContent).toContain('95.00');
    expect(tooltip.textContent).toContain('1.50M');
    expect(tooltip.textContent).toContain('+5.00% on the day');

    svg.dispatchEvent(new MouseEvent('pointerleave', { bubbles: true }));
    fixture.detectChanges();
    expect(host.querySelector('[data-testid="candle-tooltip"]')).toBeNull();
  });

  it('measures a bar against the previous close once there is one', () => {
    const { fixture, host, svg } = render([
      bar('2026-10-01', { open: 100, close: 100 }),
      bar('2026-10-02', { open: 98, high: 100, low: 90, close: 90 })
    ]);

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();

    // ArrowLeft with nothing selected lands on the newest bar: 90 against the 100 close before it
    expect(host.querySelector('[data-testid="candle-tooltip"]')?.textContent).toContain('-10.00% on the day');
  });

  it('can be read with the keyboard alone', () => {
    const { fixture, host, svg } = render([bar('2026-10-01', {}), bar('2026-10-02', {}), bar('2026-10-05', {})]);
    const press = (key: string) => {
      svg.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
      fixture.detectChanges();
    };
    const shown = () => host.querySelector('[data-testid="candle-tooltip"] strong')?.textContent;

    press('ArrowRight');
    expect(shown()).toContain('5 Oct');
    press('ArrowLeft');
    expect(shown()).toContain('2 Oct');
    press('ArrowLeft');
    press('ArrowLeft');
    expect(shown()).toContain('1 Oct');
    press('Escape');
    expect(shown()).toBeUndefined();
  });
});

function bar(date: string, overrides: Partial<Candle>): Candle {
  return { date, open: 100, high: 110, low: 95, close: 105, volume: 1000, synthetic: false, ...overrides };
}
