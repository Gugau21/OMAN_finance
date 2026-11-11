import type { StockStat } from '../types/stock';

import { getConfiguredDataSource, type DataSource } from './dataSource';
import { parseStockStat } from './responseValidation';

export type StockStatOptions = {
  source?: DataSource;
  signal?: AbortSignal;
};

const mockStockStat: StockStat = {
  symbol: 'AAPL',
  timestamp: '2026-10-29T17:07:00Z',
  price: 231.42,
  change: 1.87,
  changePercent: 0.81,
  volume: 1843200,
};

export async function getStockStat(
  symbol: string,
  options: StockStatOptions = {},
): Promise<StockStat | null> {
  const source = options.source ?? getConfiguredDataSource('stockStat');

  if (source === 'mock') {
    return symbol === mockStockStat.symbol ? { ...mockStockStat } : null;
  }

  const endpoint = `/api/stocks/${encodeURIComponent(symbol)}/stat`;
  const response = options.signal
    ? await fetch(endpoint, { signal: options.signal })
    : await fetch(endpoint);

  if (response.status === 404) {
    return null;
  }

  if (!response.ok) {
    throw new Error(`Failed to fetch stock stat: ${response.status} ${response.statusText}`);
  }

  return parseStockStat(await response.json());
}
