import type { PricePoint, Sparkline } from '../types/stock';

import { getConfiguredDataSource, type DataSource } from './dataSource';

const mockSparklines: Sparkline[] = [
  createMockSparkline('AAPL', 231.42),
  createMockSparkline('MSFT', 420.1),
  createMockSparkline('NVDA', 145.75),
];

export async function getSparklines(
  source: DataSource = getConfiguredDataSource('sparklines'),
): Promise<Sparkline[]> {
  if (source === 'mock') {
    return mockSparklines;
  }

  const response = await fetch('/api/stocks/sparkline');

  if (!response.ok) {
    throw new Error(`Failed to fetch sparklines: ${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<Sparkline[]>;
}

function createMockSparkline(symbol: string, lastPrice: number): Sparkline {
  const start = Date.parse('2026-10-29T16:08:00Z');
  const points: PricePoint[] = Array.from({ length: 60 }, (_, index) => ({
    timestamp: new Date(start + index * 60_000).toISOString(),
    price: Number((lastPrice - (59 - index) * 0.08).toFixed(2)),
  }));

  return { symbol, points };
}
