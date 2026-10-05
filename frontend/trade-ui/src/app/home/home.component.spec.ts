import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { HomeComponent } from './home.component';

const authServiceMock = {
  login: vi.fn()
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
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceMock }]
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
});