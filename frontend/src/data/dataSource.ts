export type DataSource = 'mock' | 'real';

export type DataEndpoint = 'marketStatus' | 'stocks';

export function getConfiguredDataSource(endpoint: DataEndpoint): DataSource {
  const endpointOverride =
    endpoint === 'marketStatus'
      ? import.meta.env.VITE_MOCK_MARKET_STATUS
      : import.meta.env.VITE_MOCK_STOCKS;

  if (endpointOverride === 'true') {
    return 'mock';
  }

  if (endpointOverride === 'false') {
    return 'real';
  }

  return import.meta.env.VITE_DATA_SOURCE === 'real' ? 'real' : 'mock';
}
