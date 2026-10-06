import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { TradeService } from '../api/trade.service';
import { HomeComponent } from './home.component';

const authServiceMock = {
  login: vi.fn()
};

const tradeServiceMock = {
  getBalance: vi.fn().mockReturnValue(of({ accountId: 1, cashBalance: 0, heldForOrders: 0, availableToWithdraw: 0, currency: 'INR', asOf: '' })),
  getPreferences: vi.fn().mockReturnValue(of({ currency: 'INR', theme: 'DARK', alertChannel: 'EMAIL' })),
  getCashTransactions: vi.fn().mockReturnValue(of([]))
};

describe('HomeComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem(
      'trade-ui.auth.session',
      JSON.stringify({ accessToken: 'jwt', expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: TradeService, useValue: tradeServiceMock }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('signing out clears the session', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    fixture.componentInstance.signOut();

    expect(localStorage.getItem('trade-ui.auth.session')).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith('/sign-in');
  });

  it('the funds button opens and closes the add / withdraw dialog', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('[data-testid="funds-dialog"]')).toBeNull();

    (host.querySelector('[data-testid="funds-open"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host.querySelector('[data-testid="funds-dialog"]')).not.toBeNull();

    (host.querySelector('[data-testid="funds-close"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host.querySelector('[data-testid="funds-dialog"]')).toBeNull();
  });
});