export type DataSource = 'mock' | 'real';
export type MockScenario = 'success' | 'empty' | 'error' | 'loading';

export type DataEndpoint =
  'marketStatus' | 'stocks' | 'sparklines' | 'stockStats' | 'stockStat' | 'stockCandles';

export type DataOptions = {
  source?: DataSource;
  scenario?: MockScenario;
  signal?: AbortSignal;
};

export function getConfiguredDataSource(endpoint: DataEndpoint): DataSource {
  const endpointOverride = {
    marketStatus: import.meta.env.VITE_MOCK_MARKET_STATUS,
    stocks: import.meta.env.VITE_MOCK_STOCKS,
    sparklines: import.meta.env.VITE_MOCK_SPARKLINES,
    stockStats: import.meta.env.VITE_MOCK_STOCK_STATS,
    stockStat: import.meta.env.VITE_MOCK_STOCK_STAT,
    stockCandles: import.meta.env.VITE_MOCK_STOCK_CANDLES,
  }[endpoint];

  if (endpointOverride === 'true') {
    return 'mock';
  }

  if (endpointOverride === 'false') {
    return 'real';
  }

  return import.meta.env.VITE_DATA_SOURCE === 'real' ? 'real' : 'mock';
}

export function getConfiguredScenario(endpoint: DataEndpoint): MockScenario {
  const endpointOverride = {
    marketStatus: import.meta.env.VITE_MARKET_STATUS_SCENARIO,
    stocks: import.meta.env.VITE_STOCKS_SCENARIO,
    sparklines: import.meta.env.VITE_SPARKLINES_SCENARIO,
    stockStats: import.meta.env.VITE_STOCK_STATS_SCENARIO,
    stockStat: import.meta.env.VITE_STOCK_STAT_SCENARIO,
    stockCandles: import.meta.env.VITE_STOCK_CANDLES_SCENARIO,
  }[endpoint];

  if (endpointOverride === 'success') {
    return 'success';
  }

  if (endpointOverride === 'empty') {
    return 'empty';
  }

  if (endpointOverride === 'error') {
    return 'error';
  }

  if (endpointOverride === 'loading') {
    return 'loading';
  }

  return import.meta.env.VITE_DATA_SCENARIO;
}

export function waitForever(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Stocks mock request aborted', 'AbortError'));
      return;
    }

    signal?.addEventListener(
      'abort',
      () => reject(new DOMException('Stocks mock request aborted', 'AbortError')),
      { once: true },
    );
  });
}
