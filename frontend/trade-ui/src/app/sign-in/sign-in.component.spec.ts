import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { SignInComponent } from './sign-in.component';

@Component({ template: '' })
class HomeStubComponent {}

const authServiceMock = {
  login: vi.fn()
};

describe('SignInComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    authServiceMock.login.mockReset();

    await TestBed.configureTestingModule({
      imports: [SignInComponent],
      providers: [
        provideRouter([
          { path: '', component: HomeStubComponent },
          { path: 'sign-in', component: SignInComponent }
        ]),
        { provide: AuthService, useValue: authServiceMock }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('valid credentials sign in and redirect', async () => {
    const fixture = TestBed.createComponent(SignInComponent);
    const component = fixture.componentInstance;
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    authServiceMock.login.mockReturnValue(
      of({ accessToken: 'jwt', expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );

    component.signInForm.setValue({ username: 'team5', password: 'secret' });
    fixture.detectChanges();

    component.submit();

    await fixture.whenStable();

    expect(authServiceMock.login).toHaveBeenCalledWith({ username: 'team5', password: 'secret' });
    expect(navigateSpy).toHaveBeenCalledWith('/');
    expect(localStorage.getItem('trade-ui.auth.session')).toContain('jwt');
  });

  it.each([
    ['/orders?status=NEW', '/orders?status=NEW'],
    ['https://evil.com', '/'],
    ['//evil.com', '/']
  ])('after sign-in the return address %s lands on %s', async (returnUrl, landing) => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl(`/sign-in?returnUrl=${encodeURIComponent(returnUrl)}`);
    const fixture = TestBed.createComponent(SignInComponent);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    authServiceMock.login.mockReturnValue(
      of({ accessToken: 'jwt', expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );

    fixture.componentInstance.signInForm.setValue({ username: 'team5', password: 'secret' });
    fixture.componentInstance.submit();
    await fixture.whenStable();

    expect(navigateSpy).toHaveBeenCalledWith(landing);
  });

  it('an unsafe return address is removed from the address bar', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/sign-in?returnUrl=hello.com');

    TestBed.createComponent(SignInComponent);
    await vi.waitFor(() => expect(router.url).toBe('/sign-in'));
  });

  it('a safe return address is left in the address bar', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/sign-in?returnUrl=%2Forders');

    TestBed.createComponent(SignInComponent);
    await new Promise((resolve) => setTimeout(resolve));

    expect(router.url).toBe('/sign-in?returnUrl=%2Forders');
  });

  it('invalid credentials show a readable error', () => {
    const fixture = TestBed.createComponent(SignInComponent);
    const component = fixture.componentInstance;
    authServiceMock.login.mockReturnValue(
      throwError(() => ({ status: 401, error: { errorCode: 'AUTH-401', message: 'Unauthorised' } }))
    );

    component.signInForm.setValue({ username: 'team5', password: 'wrong' });
    fixture.detectChanges();

    component.submit();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('Authentication failed. Sign in again and retry.');
  });

  it('empty fields are blocked by validation', () => {
    const fixture = TestBed.createComponent(SignInComponent);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    component.submit();
    fixture.detectChanges();

    expect(authServiceMock.login).not.toHaveBeenCalled();

    const compiled = fixture.nativeElement as HTMLElement;
    const button = compiled.querySelector('[data-testid="signin-submit"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(compiled.textContent).toContain('Username is required.');
    expect(compiled.textContent).toContain('Password is required.');
  });
});