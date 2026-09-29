import { provideHttpClient } from '@angular/common/http';
import { EnvironmentProviders, Provider, makeEnvironmentProviders } from '@angular/core';

import { provideApiConfiguration as provideAuthApiConfiguration } from './generated/auth-api/api-configuration';
import { provideApiConfiguration as provideTradeApiConfiguration } from './generated/trade-api/api-configuration';

export interface ApiUrls {
  tradeApi: string;
  authApi: string;
}

/** Defaults match the `servers` entries in the two contracts. */
const DEFAULT_URLS: ApiUrls = {
  tradeApi: 'http://localhost:8080',
  authApi: 'http://localhost:3000'
};

export function provideApi(urls: Partial<ApiUrls> = {}): EnvironmentProviders {
  const { tradeApi, authApi } = { ...DEFAULT_URLS, ...urls };
  const providers: (Provider | EnvironmentProviders)[] = [
    provideHttpClient(),
    provideTradeApiConfiguration(tradeApi),
    provideAuthApiConfiguration(authApi)
  ];
  return makeEnvironmentProviders(providers);
}
