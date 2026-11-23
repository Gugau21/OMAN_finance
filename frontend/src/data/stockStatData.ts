import type { StockStat } from '../types/stock';

import { getConfiguredDataSource, type DataSource, type MockScenario } from './dataSource';
import { parseStockStat } from './responseValidation';
import { getJson } from '../lib/http/client';
import { HttpError } from '../lib/http/errors';

export type StockStatOptions = {
  source?: DataSource;
  scenario?: MockScenario;
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
    return getMockStockStat(symbol, options.scenario, options.signal);
  }

  try {
    const value = await getJson(`/api/stocks/${encodeURIComponent(symbol)}/stat`, {
      signal: options.signal,
      resourceName: 'stock stat',
    });
    return parseStockStat(value);
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

async function getMockStockStat(
  symbol: string,
  scenario: MockScenario = getConfiguredScenario(),
  signal?: AbortSignal,
): Promise<StockStat | null> {
  if (scenario === 'empty' || symbol !== mockStockStat.symbol) {
    return null;
  }

  if (scenario === 'error') {
    throw new Error('Stock stat mock request failed');
  }

  if (scenario === 'loading') {
    return waitForever(signal);
  }

  return { ...mockStockStat };
}

function getConfiguredScenario(): MockScenario {
  const configuredScenario = import.meta.env.VITE_STOCK_STAT_SCENARIO;

  return configuredScenario === 'empty' ||
    configuredScenario === 'error' ||
    configuredScenario === 'loading'
    ? configuredScenario
    : 'success';
}

function waitForever(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Stock stat mock request aborted', 'AbortError'));
      return;
    }

    signal?.addEventListener(
      'abort',
      () => reject(new DOMException('Stock stat mock request aborted', 'AbortError')),
      { once: true },
    );
  });
}
