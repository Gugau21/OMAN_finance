export type DataSource = 'mock' | 'real';

export type DataEndpoint = 'marketStatus' | 'stocks' | 'sparklines';

export function getConfiguredDataSource(endpoint: DataEndpoint): DataSource {
  const endpointOverride = {
    marketStatus: import.meta.env.VITE_MOCK_MARKET_STATUS,
    stocks: import.meta.env.VITE_MOCK_STOCKS,
    sparklines: import.meta.env.VITE_MOCK_SPARKLINES,
  }[endpoint];

  if (endpointOverride === 'true') {
    return 'mock';
  }

  if (endpointOverride === 'false') {
    return 'real';
  }

  return import.meta.env.VITE_DATA_SOURCE === 'real' ? 'real' : 'mock';
}
