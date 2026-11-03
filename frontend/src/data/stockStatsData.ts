import type { StockStat } from '../types/stock';

import { getConfiguredDataSource, type DataSource } from './dataSource';
import { parseStockStats } from './responseValidation';

const mockStockStats: StockStat[] = [
  {
    symbol: 'AAPL',
    timestamp: '2026-10-29T17:07:00Z',
    price: 231.42,
    change: 1.87,
    changePercent: 0.81,
    volume: 1843200,
  },
  {
    symbol: 'MSFT',
    timestamp: '2026-10-29T17:07:00Z',
    price: 420.1,
    change: -2.35,
    changePercent: -0.56,
    volume: 1264800,
  },
  {
    symbol: 'NVDA',
    timestamp: '2026-10-29T17:07:00Z',
    price: 145.75,
    change: 3.12,
    changePercent: 2.19,
    volume: 2389100,
  },
];

export async function getStockStats(
  source: DataSource = getConfiguredDataSource('stockStats'),
): Promise<StockStat[]> {
  if (source === 'mock') {
    return mockStockStats;
  }

  const response = await fetch('/api/stocks/stat');

  if (!response.ok) {
    throw new Error(`Failed to fetch stock stats: ${response.status} ${response.statusText}`);
  }

  return parseStockStats(await response.json());
}
