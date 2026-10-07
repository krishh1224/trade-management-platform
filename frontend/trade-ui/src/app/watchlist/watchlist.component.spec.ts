import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { ThemeService } from '../theme/theme.service';
import { WatchlistComponent } from './watchlist.component';
import { provideNotificationCountStub } from '../notifications/notification-count.testing';

const ACTIVE_ALERT = {
  alertId: 1,
  watchlistId: 'w1',
  symbol: 'AAPL',
  instrumentName: 'Apple',
  direction: 'ABOVE' as const,
  threshold: 190,
  status: 'ACTIVE' as const,
  createdAt: '2026-01-01T00:00:00Z',
  triggeredAt: null,
  triggerPrice: null,
  lastPrice: 180
};

const TRIGGERED_ALERT = {
  ...ACTIVE_ALERT,
  alertId: 2,
  direction: 'BELOW' as const,
  threshold: 175,
  status: 'TRIGGERED' as const,
  triggeredAt: '2026-01-02T10:15:00Z',
  triggerPrice: 174.5
};

function alertList(alerts = [ACTIVE_ALERT, TRIGGERED_ALERT], max = 20) {
  return {
    maxActiveAlerts: max,
    activeAlerts: alerts.filter((alert) => alert.status === 'ACTIVE').length,
    alerts
  };
}

describe('WatchlistComponent', () => {
  let component: WatchlistComponent;
  let fixture: ComponentFixture<WatchlistComponent>;

  const authSessionMock = {
    claims: signal({
      sub: 'alice',
      accountId: 42,
      roles: ['USER'],
      iat: 1,
      exp: Math.floor(Date.now() / 1000) + 3600
    }),
    signOut: vi.fn()
  };

  const tradeServiceMock = {
    listWatchlistGroups: vi.fn().mockReturnValue(
      of([{ watchlistGroupId: 'g1', name: 'Main', createdAt: '2026-01-01T00:00:00Z' }])
    ),
    getWatchlist: vi.fn().mockReturnValue(
      of([
        {
          watchlistId: 'w1',
          watchlistGroupId: 'g1',
          watchlistName: 'Main',
          instrumentId: 11,
          symbol: 'AAPL',
          instrumentName: 'Apple',
          addedAt: '2026-01-01T00:00:00Z',
          lastPrice: 180,
          changePct: 1.2,
          currency: 'USD'
        }
      ])
    ),
    createWatchlistGroup: vi.fn(),
    getInstruments: vi.fn().mockReturnValue(of([])),
    addToWatchlist: vi.fn().mockReturnValue(of({})),
    removeFromWatchlist: vi.fn().mockReturnValue(of(void 0)),
    listPriceAlerts: vi.fn().mockReturnValue(of(alertList())),
    deleteWatchlistGroup: vi.fn().mockReturnValue(of(void 0))
  };

  const theme = signal<'dark' | 'light'>('dark');
  const themeServiceMock = {
    theme,
    initialize: vi.fn(),
    toggleTheme: vi.fn(() => theme.set(theme() === 'dark' ? 'light' : 'dark'))
  };

  const routerMock = {
    navigate: vi.fn().mockResolvedValue(true),
    navigateByUrl: vi.fn().mockResolvedValue(true)
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    authSessionMock.claims.set({
      sub: 'alice',
      accountId: 42,
      roles: ['USER'],
      iat: 1,
      exp: Math.floor(Date.now() / 1000) + 3600
    });
    tradeServiceMock.listWatchlistGroups.mockReturnValue(
      of([{ watchlistGroupId: 'g1', name: 'Main', createdAt: '2026-01-01T00:00:00Z' }])
    );
    tradeServiceMock.getWatchlist.mockReturnValue(
      of([
        {
          watchlistId: 'w1',
          watchlistGroupId: 'g1',
          watchlistName: 'Main',
          instrumentId: 11,
          symbol: 'AAPL',
          instrumentName: 'Apple',
          addedAt: '2026-01-01T00:00:00Z',
          lastPrice: 180,
          changePct: 1.2,
          currency: 'USD'
        }
      ])
    );

    tradeServiceMock.listPriceAlerts.mockReturnValue(of(alertList()));

    await TestBed.configureTestingModule({
      imports: [WatchlistComponent],
      providers: [
        provideNotificationCountStub(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TradeService, useValue: tradeServiceMock },
        { provide: AuthSessionService, useValue: authSessionMock },
        { provide: ThemeService, useValue: themeServiceMock },
        { provide: Router, useValue: routerMock },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } }
        }
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WatchlistComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads groups and watchlist for the selected group', () => {
    expect(tradeServiceMock.listWatchlistGroups).toHaveBeenCalledTimes(1);
    expect(component.selectedGroupId()).toBe('g1');
    expect(tradeServiceMock.getWatchlist).toHaveBeenCalledWith(42, 'g1');
    expect(component.rowsForSelectedGroup()).toHaveLength(1);
  });

  it('renders a deep link from a watchlist row into the advisor page', () => {
    const link = fixture.nativeElement.querySelector('[data-testid="advisor-link-AAPL"]') as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(link.textContent).toContain('View signal');
  });

  it('selects a group and refreshes watchlist with group filter', () => {
    component.selectGroup('g2');

    expect(routerMock.navigate).toHaveBeenCalled();
    expect(tradeServiceMock.getWatchlist).toHaveBeenLastCalledWith(42, 'g2');
  });

  it('clears group filter and loads unfiltered watchlist', () => {
    component.selectGroup('g2');
    component.clearGroupFilter();

    expect(component.selectedGroupId()).toBeNull();
    expect(tradeServiceMock.getWatchlist).toHaveBeenLastCalledWith(42, undefined);
  });

  it('creates a group and selects it on success', () => {
    tradeServiceMock.createWatchlistGroup.mockReturnValue(
      of({ watchlistGroupId: 'g2', name: 'Tech', createdAt: '2026-01-01T00:00:00Z' })
    );
    component.createGroupForm.patchValue({ name: 'Tech' });

    component.createWatchlistGroup();

    expect(tradeServiceMock.createWatchlistGroup).toHaveBeenCalledWith({ name: 'Tech' });
    expect(component.selectedGroupId()).toBe('g2');
  });

  it('shows duplicate-name message when group creation returns 409', () => {
    tradeServiceMock.createWatchlistGroup.mockReturnValue(throwError(() => ({ status: 409 })));
    component.createGroupForm.patchValue({ name: 'Main' });

    component.createWatchlistGroup();

    expect(component.createGroupError()).toContain('already exists');
  });

  it('adds instrument using uppercase symbol and selected group', () => {
    component.addForm.patchValue({ symbol: ' msft ' });

    component.submitAddForm();

    expect(tradeServiceMock.addToWatchlist).toHaveBeenCalledWith({
      accountId: 42,
      symbol: 'MSFT',
      watchlistGroupId: 'g1'
    });
    expect(component.addFormSuccess()).toContain('MSFT added');
  });

  it('shows instrument options while typing and selects one', () => {
    tradeServiceMock.getInstruments.mockReturnValue(
      of([{ symbol: 'AAPL', name: 'Apple Inc.', currency: 'USD' }])
    );
    component.addForm.patchValue({ symbol: 'AAP' });

    component.onInstrumentInput();

    expect(component.showInstrumentDropdown()).toBe(true);
    expect(component.filteredInstrumentOptions()).toHaveLength(1);
    component.selectInstrument(component.filteredInstrumentOptions()[0]);
    expect(component.addForm.get('symbol')?.value).toBe('AAPL');
    expect(component.showInstrumentDropdown()).toBe(false);
  });

  it('hides instrument dropdown when input is empty', () => {
    component.addForm.patchValue({ symbol: '' });

    component.onInstrumentInput();

    expect(component.showInstrumentDropdown()).toBe(false);
  });

  it('shows instrument-not-found message when add returns 404', () => {
    tradeServiceMock.addToWatchlist.mockReturnValueOnce(throwError(() => ({ status: 404 })));
    component.addForm.patchValue({ symbol: 'UNKNOWN' });

    component.submitAddForm();

    expect(component.addFormError()).toContain('Instrument not found');
  });

  it('removes row on success and sets error on failure', () => {
    const row = component.watchlistRows()[0];
    component.removeFromWatchlist(row);
    expect(component.watchlistRows()).toHaveLength(0);

    tradeServiceMock.removeFromWatchlist.mockReturnValueOnce(throwError(() => new Error('boom')));
    component.watchlistRows.set([row]);
    component.removeFromWatchlist(row);
    expect(component.watchlistError()).toContain('Failed to remove item');
  });

  it('toggles theme and profile menu, then signs out', () => {
    expect(component.theme()).toBe('dark');
    component.toggleTheme();
    expect(component.theme()).toBe('light');

    expect(component.profileMenuOpen()).toBe(false);
    component.toggleProfileMenu();
    expect(component.profileMenuOpen()).toBe(true);
    component.closeProfileMenu();
    expect(component.profileMenuOpen()).toBe(false);

    component.signOut();
    expect(authSessionMock.signOut).toHaveBeenCalled();
    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/sign-in');
  });

  it('shows the live price beside each watched instrument', () => {
    const row = fixture.nativeElement.querySelector('table[aria-label="Watchlist table"] tbody tr');

    expect(row.textContent).toContain('AAPL');
    expect(row.textContent).toContain('$180.00');
    expect(row.textContent).toContain('+1.20%');
  });

  it('marks instruments that have active alerts and links the Alert button to the Alerts page', () => {
    expect(component.activeAlertsFor(component.watchlistRows()[0])).toBe(1);
    const badge: HTMLAnchorElement = fixture.nativeElement.querySelector('.alert-count');
    expect(badge.textContent).toContain('1 alert');

    component.setAlert(component.watchlistRows()[0]);

    expect(routerMock.navigate).toHaveBeenCalledWith(['/alerts'], { queryParams: { watchlistId: 'w1' } });
  });

  it('shows the currency symbol the instrument is quoted in', () => {
    expect(component.currencySymbol(component.watchlistRows()[0])).toBe('$');

    tradeServiceMock.getWatchlist.mockReturnValue(of([{
      ...component.watchlistRows()[0].item, watchlistId: 'w2', symbol: 'INFY.NS', currency: 'INR', lastPrice: 1502.35
    }]));
    component.selectGroup('g1');
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('table[aria-label="Watchlist table"] tbody tr');
    expect(row.textContent).toContain('₹1,502.35');
  });

  it('refreshes alert counts when an instrument is removed', () => {
    tradeServiceMock.listPriceAlerts.mockClear();

    component.removeFromWatchlist(component.watchlistRows()[0]);

    expect(tradeServiceMock.listPriceAlerts).toHaveBeenCalledTimes(1);
  });

  describe('deleting a watchlist', () => {
    const FAVOURITE = { watchlistGroupId: 'g1', name: 'Favourite', createdAt: '2026-01-01T00:00:00Z' };
    const SWING = { watchlistGroupId: 'g2', name: 'Swing ideas', createdAt: '2026-01-02T00:00:00Z' };

    beforeEach(() => {
      tradeServiceMock.listWatchlistGroups.mockReturnValue(of([FAVOURITE, SWING]));
      fixture = TestBed.createComponent(WatchlistComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('offers delete on custom watchlists only, never on Favourite', () => {
      const deleteButtons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.group-delete'));

      expect(deleteButtons).toHaveLength(1);
      expect(deleteButtons[0].getAttribute('aria-label')).toBe('Delete watchlist Swing ideas');
      expect(component.isFavourite(FAVOURITE)).toBe(true);
      expect(component.isFavourite({ ...FAVOURITE, name: ' favourite ' })).toBe(true);
      expect(component.isFavourite(SWING)).toBe(false);
    });

    it('asks for confirmation, and cancelling deletes nothing', () => {
      component.requestDeleteGroup(SWING);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.group-confirm').textContent).toContain('Delete "Swing ideas"?');

      component.cancelDeleteGroup();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.group-confirm')).toBeNull();
      expect(tradeServiceMock.deleteWatchlistGroup).not.toHaveBeenCalled();
    });

    it('deletes the selected watchlist, falls back to Favourite and reloads alerts', () => {
      component.selectGroup('g2');
      tradeServiceMock.listPriceAlerts.mockClear();

      component.requestDeleteGroup(SWING);
      component.confirmDeleteGroup(SWING);

      expect(tradeServiceMock.deleteWatchlistGroup).toHaveBeenCalledWith('g2');
      expect(component.groups().map((group) => group.watchlistGroupId)).toEqual(['g1']);
      expect(component.selectedGroupId()).toBe('g1');
      expect(tradeServiceMock.getWatchlist).toHaveBeenLastCalledWith(42, 'g1');
      expect(tradeServiceMock.listPriceAlerts).toHaveBeenCalledTimes(1);
      expect(component.groupPendingDelete()).toBeNull();
    });

    it('shows a catalogue message when the API refuses', () => {
      tradeServiceMock.deleteWatchlistGroup.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
        status: 403,
        error: { errorCode: 'ACC-403', message: 'Account not active' }
      })));

      component.confirmDeleteGroup(SWING);

      expect(component.groupDeleteError()).toContain('this session cannot access it');
      expect(component.groups()).toHaveLength(2);
    });
  });

});
