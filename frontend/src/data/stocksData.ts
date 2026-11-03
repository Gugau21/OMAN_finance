import type { Stock } from '../types/stock';

import { getConfiguredDataSource, type DataSource } from './dataSource';

const mockStocks: Stock[] = [
  { symbol: 'AAPL', name: 'Apple Inc.' },
  { symbol: 'MSFT', name: 'Microsoft Corporation' },
  { symbol: 'NVDA', name: 'NVIDIA Corporation' },
];

export async function getStocks(source: DataSource = getConfiguredDataSource()): Promise<Stock[]> {
  if (source === 'mock') {
    return mockStocks;
  }

  const response = await fetch('/api/stocks');

  if (!response.ok) {
    throw new Error(`Failed to fetch stocks: ${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<Stock[]>;
}
