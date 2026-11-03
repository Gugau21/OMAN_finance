export type DataSource = 'mock' | 'real';

export type DataEndpoint = 'marketStatus' | 'stocks' | 'sparklines' | 'stockStats';

export function getConfiguredDataSource(endpoint: DataEndpoint): DataSource {
  const endpointOverride = {
    marketStatus: import.meta.env.VITE_MOCK_MARKET_STATUS,
    stocks: import.meta.env.VITE_MOCK_STOCKS,
    sparklines: import.meta.env.VITE_MOCK_SPARKLINES,
    stockStats: import.meta.env.VITE_MOCK_STOCK_STATS,
  }[endpoint];

  if (endpointOverride === 'true') {
    return 'mock';
  }

  if (endpointOverride === 'false') {
    return 'real';
  }

  return import.meta.env.VITE_DATA_SOURCE === 'real' ? 'real' : 'mock';
}
