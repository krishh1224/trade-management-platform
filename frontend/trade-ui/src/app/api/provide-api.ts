import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { EnvironmentProviders, Provider, makeEnvironmentProviders } from '@angular/core';

import { ADVISOR_API_URL } from './advisor.service';
import { bearerTokenInterceptor } from './bearer-token.interceptor';
import { provideApiConfiguration as provideAuthApiConfiguration } from './generated/auth-api/api-configuration';
import { provideApiConfiguration as provideTradeApiConfiguration } from './generated/trade-api/api-configuration';

export interface ApiUrls {
  tradeApi: string;
  authApi: string;
  advisorApi?: string;
}

/** Defaults match the `servers` entries in the contracts. */
const DEFAULT_URLS: Required<ApiUrls> = {
  tradeApi: 'http://localhost:8081',
  authApi: 'http://localhost:3000',
  advisorApi: 'http://localhost:8083'
};

export function provideApi(urls: Partial<ApiUrls> = {}): EnvironmentProviders {
  const { tradeApi, authApi, advisorApi } = { ...DEFAULT_URLS, ...urls };
  const providers: (Provider | EnvironmentProviders)[] = [
    provideHttpClient(withInterceptors([bearerTokenInterceptor({ tradeApi, authApi, advisorApi })])),
    provideTradeApiConfiguration(tradeApi),
    provideAuthApiConfiguration(authApi),
    { provide: ADVISOR_API_URL, useValue: advisorApi }
  ];
  return makeEnvironmentProviders(providers);
}
