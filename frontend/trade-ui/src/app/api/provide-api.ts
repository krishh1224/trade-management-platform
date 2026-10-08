import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { EnvironmentProviders, Provider, makeEnvironmentProviders } from '@angular/core';

import { ADVISOR_API_URL } from './advisor.service';
import { bearerTokenInterceptor } from './bearer-token.interceptor';
import { refreshTokenInterceptor } from './refresh-token.interceptor';
import { provideApiConfiguration as provideAuthApiConfiguration } from './generated/auth-api/api-configuration';
import { provideApiConfiguration as provideTradeApiConfiguration } from './generated/trade-api/api-configuration';

export interface ApiUrls {
  tradeApi: string;
  authApi: string;
  advisorApi?: string;
}

/** Defaults match the `servers` entries in the contracts. */
const DEFAULT_URLS = {
  tradeApi: 'http://localhost:8081',
  authApi: 'http://localhost:3000'
};

export function provideApi(urls: Partial<ApiUrls> = {}): EnvironmentProviders {
  const { tradeApi, authApi } = { ...DEFAULT_URLS, ...urls };
  // The advisor is a module of the Trade API, so it shares that origin unless a test points it elsewhere.
  const advisorApi = urls.advisorApi ?? tradeApi;
  const providers: (Provider | EnvironmentProviders)[] = [
    provideHttpClient(
      withInterceptors([
        refreshTokenInterceptor({ tradeApi, authApi, advisorApi }),
        bearerTokenInterceptor({ tradeApi, authApi, advisorApi })
      ])
    ),
    provideTradeApiConfiguration(tradeApi),
    provideAuthApiConfiguration(authApi),
    { provide: ADVISOR_API_URL, useValue: advisorApi }
  ];
  return makeEnvironmentProviders(providers);
}
