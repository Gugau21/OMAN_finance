import type { MarketStatus } from '../types/market';

import { getConfiguredDataSource, type DataSource, type MockScenario } from './dataSource';
import { parseMarketStatus } from './responseValidation';

export type MarketDataSource = DataSource;
export type MarketStatusOptions = {
  source?: MarketDataSource;
  scenario?: MockScenario;
  signal?: AbortSignal;
};

const mockMarketStatus: MarketStatus = {
  timestamp: '2026-10-29T13:08:00-04:00',
  isOpen: true,
  nextOpen: '2026-10-30T09:30:00-04:00',
  nextClose: '2026-10-29T16:00:00-04:00',
};

export async function getMarketStatus(
  sourceOrOptions: MarketDataSource | MarketStatusOptions = {},
): Promise<MarketStatus | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('marketStatus');

  if (source === 'mock') {
    return getMockMarketStatus(options.scenario, options.signal);
  }

  const response = options.signal
    ? await fetch('/api/market/status', { signal: options.signal })
    : await fetch('/api/market/status');

  if (!response.ok) {
    throw new Error(`Failed to fetch market status: ${response.status} ${response.statusText}`);
  }

  return parseMarketStatus(await response.json());
}

async function getMockMarketStatus(
  scenario: MockScenario = getConfiguredScenario(),
  signal?: AbortSignal,
): Promise<MarketStatus | null> {
  if (scenario === 'empty') {
    return null;
  }

  if (scenario === 'error') {
    throw new Error('Market status mock request failed');
  }

  if (scenario === 'loading') {
    return waitForever(signal);
  }

  return mockMarketStatus;
}

function getConfiguredScenario(): MockScenario {
  const configuredScenario = import.meta.env.VITE_MARKET_STATUS_SCENARIO;

  return configuredScenario === 'empty' ||
    configuredScenario === 'error' ||
    configuredScenario === 'loading'
    ? configuredScenario
    : 'success';
}

function waitForever(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    if (signal?.aborted) {
      reject(new Error('Market status mock request aborted'));
      return;
    }

    signal?.addEventListener(
      'abort',
      () => reject(new Error('Market status mock request aborted')),
      { once: true },
    );
  });
}
