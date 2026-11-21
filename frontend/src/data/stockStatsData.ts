import type { StockStat } from '../types/stock';

import { getConfiguredDataSource, type DataSource, type MockScenario } from './dataSource';
import { parseStockStats } from './responseValidation';

export type StockStatsOptions = {
  source?: DataSource;
  scenario?: MockScenario;
  signal?: AbortSignal;
};

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
  sourceOrOptions: DataSource | StockStatsOptions = {},
): Promise<StockStat[] | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('stockStats');

  if (source === 'mock') {
    return getMockStockStats(options.scenario, options.signal);
  }

  const response = options.signal
    ? await fetch('/api/stocks/stat', { signal: options.signal })
    : await fetch('/api/stocks/stat');

  if (!response.ok) {
    throw new Error(`Failed to fetch stock stats: ${response.status} ${response.statusText}`);
  }

  return parseStockStats(await response.json());
}

async function getMockStockStats(
  scenario: MockScenario = getConfiguredScenario(),
  signal?: AbortSignal,
): Promise<StockStat[] | null> {
  if (scenario === 'empty') {
    return null;
  }

  if (scenario === 'error') {
    throw new Error('Stock stats mock request failed');
  }

  if (scenario === 'loading') {
    return waitForever(signal);
  }

  return mockStockStats;
}

function getConfiguredScenario(): MockScenario {
  const configuredScenario = import.meta.env.VITE_STOCK_STATS_SCENARIO;

  return configuredScenario === 'empty' ||
    configuredScenario === 'error' ||
    configuredScenario === 'loading'
    ? configuredScenario
    : 'success';
}

function waitForever(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Stock stats mock request aborted', 'AbortError'));
      return;
    }

    signal?.addEventListener(
      'abort',
      () => reject(new DOMException('Stock stats mock request aborted', 'AbortError')),
      { once: true },
    );
  });
}
