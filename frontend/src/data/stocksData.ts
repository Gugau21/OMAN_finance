import type { Stock } from '../types/stock';

import { getConfiguredDataSource, type DataSource, type MockScenario } from './dataSource';
import { parseStocks } from './responseValidation';

export type StocksOptions = {
  source?: DataSource;
  scenario?: MockScenario;
  signal?: AbortSignal;
};

const mockStocks: Stock[] = [
  { symbol: 'AAPL', name: 'Apple Inc.' },
  { symbol: 'MSFT', name: 'Microsoft Corporation' },
  { symbol: 'NVDA', name: 'NVIDIA Corporation' },
];

export async function getStocks(
  sourceOrOptions: DataSource | StocksOptions = {},
): Promise<Stock[] | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('stocks');

  if (source === 'mock') {
    return getMockStocks(options.scenario, options.signal);
  }

  const response = options.signal
    ? await fetch('/api/stocks', { signal: options.signal })
    : await fetch('/api/stocks');

  if (!response.ok) {
    throw new Error(`Failed to fetch stocks: ${response.status} ${response.statusText}`);
  }

  return parseStocks(await response.json());
}

async function getMockStocks(
  scenario: MockScenario = getConfiguredScenario(),
  signal?: AbortSignal,
): Promise<Stock[] | null> {
  if (scenario === 'empty') {
    return null;
  }

  if (scenario === 'error') {
    throw new Error('Stocks mock request failed');
  }

  if (scenario === 'loading') {
    return waitForever(signal);
  }

  return mockStocks;
}

function getConfiguredScenario(): MockScenario {
  const configuredScenario = import.meta.env.VITE_STOCKS_SCENARIO;

  return configuredScenario === 'empty' ||
    configuredScenario === 'error' ||
    configuredScenario === 'loading'
    ? configuredScenario
    : 'success';
}

function waitForever(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    if (signal?.aborted) {
      reject(new Error('Stocks mock request aborted'));
      return;
    }

    signal?.addEventListener('abort', () => reject(new Error('Stocks mock request aborted')), {
      once: true,
    });
  });
}
