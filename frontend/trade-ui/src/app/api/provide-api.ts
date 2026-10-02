import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { EnvironmentProviders, Provider, makeEnvironmentProviders } from '@angular/core';

import { bearerTokenInterceptor } from './bearer-token.interceptor';
import { provideApiConfiguration as provideAuthApiConfiguration } from './generated/auth-api/api-configuration';
import { provideApiConfiguration as provideTradeApiConfiguration } from './generated/trade-api/api-configuration';

export interface ApiUrls {
  tradeApi: string;
  authApi: string;
}

// trade-api runs on 8081 locally (server.port in trade-api), not 8080
const DEFAULT_URLS: ApiUrls = {
  tradeApi: 'http://localhost:8081',
  authApi: 'http://localhost:3000'
};

export function provideApi(urls: Partial<ApiUrls> = {}): EnvironmentProviders {
  const { tradeApi, authApi } = { ...DEFAULT_URLS, ...urls };
  const providers: (Provider | EnvironmentProviders)[] = [
    provideHttpClient(withInterceptors([bearerTokenInterceptor({ tradeApi, authApi })])),
    provideTradeApiConfiguration(tradeApi),
    provideAuthApiConfiguration(authApi)
  ];
  return makeEnvironmentProviders(providers);
}
