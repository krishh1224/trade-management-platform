import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { map } from 'rxjs';

import { AuthSessionService } from './auth-session.service';
import { safeReturnUrl } from './return-url';

/**
 * Usability control only: it stops a signed-out visitor seeing a screen that cannot load. The bundle
 * is public, and authorisation is decided by the Trade REST API on every call.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  return authSession.ensureActiveSession().pipe(
    map((isActive) =>
      isActive ? true : router.createUrlTree(['/sign-in'], { queryParams: { returnUrl: state.url } })
    )
  );
};

export const guestGuard: CanActivateFn = (route) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  return authSession.ensureActiveSession().pipe(
    map((isActive) =>
      isActive ? router.parseUrl(safeReturnUrl(route.queryParamMap.get('returnUrl'))) : true
    )
  );
};
