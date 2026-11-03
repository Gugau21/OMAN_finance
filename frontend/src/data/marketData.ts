import type { MarketStatus } from '../types/market';

export type MarketDataSource = 'mock' | 'real';

const mockMarketStatus: MarketStatus = {
  timestamp: '2026-10-29T13:08:00-04:00',
  isOpen: true,
  nextOpen: '2026-10-30T09:30:00-04:00',
  nextClose: '2026-10-29T16:00:00-04:00',
};

export async function getMarketStatus(
  source: MarketDataSource = getConfiguredDataSource(),
): Promise<MarketStatus> {
  if (source === 'mock') {
    return mockMarketStatus;
  }

  const response = await fetch('/api/market/status');

  if (!response.ok) {
    throw new Error(
      `Failed to fetch market status: ${response.status} ${response.statusText}`,
    );
  }

  return response.json() as Promise<MarketStatus>;
}

function getConfiguredDataSource(): MarketDataSource {
  return import.meta.env.VITE_DATA_SOURCE === 'real' ? 'real' : 'mock';
}
