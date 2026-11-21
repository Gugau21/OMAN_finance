import type { PricePoint, Sparkline } from '../types/stock';

import { getConfiguredDataSource, type DataSource, type MockScenario } from './dataSource';
import { parseSparklines } from './responseValidation';

export type SparklinesOptions = {
  source?: DataSource;
  scenario?: MockScenario;
  signal?: AbortSignal;
};

const mockSparklines: Sparkline[] = [
  createMockSparkline('AAPL', 231.42),
  createMockSparkline('MSFT', 420.1),
  createMockSparkline('NVDA', 145.75),
];

export async function getSparklines(
  sourceOrOptions: DataSource | SparklinesOptions = {},
): Promise<Sparkline[] | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('sparklines');

  if (source === 'mock') {
    return getMockSparklines(options.scenario, options.signal);
  }

  const response = options.signal
    ? await fetch('/api/stocks/sparkline', { signal: options.signal })
    : await fetch('/api/stocks/sparkline');

  if (!response.ok) {
    throw new Error(`Failed to fetch sparklines: ${response.status} ${response.statusText}`);
  }

  return parseSparklines(await response.json());
}

async function getMockSparklines(
  scenario: MockScenario = getConfiguredScenario(),
  signal?: AbortSignal,
): Promise<Sparkline[] | null> {
  if (scenario === 'empty') {
    return null;
  }

  if (scenario === 'error') {
    throw new Error('Sparklines mock request failed');
  }

  if (scenario === 'loading') {
    return waitForever(signal);
  }

  return mockSparklines;
}

function getConfiguredScenario(): MockScenario {
  const configuredScenario = import.meta.env.VITE_SPARKLINES_SCENARIO;

  return configuredScenario === 'empty' ||
    configuredScenario === 'error' ||
    configuredScenario === 'loading'
    ? configuredScenario
    : 'success';
}

function waitForever(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Sparklines mock request aborted', 'AbortError'));
      return;
    }

    signal?.addEventListener(
      'abort',
      () => reject(new DOMException('Sparklines mock request aborted', 'AbortError')),
      { once: true },
    );
  });
}

function createMockSparkline(symbol: string, lastPrice: number): Sparkline {
  const start = Date.parse('2026-10-29T16:08:00Z');
  const points: PricePoint[] = Array.from({ length: 60 }, (_, index) => ({
    timestamp: new Date(start + index * 60_000).toISOString(),
    price: Number((lastPrice - (59 - index) * 0.08).toFixed(2)),
  }));

  return { symbol, points };
}
