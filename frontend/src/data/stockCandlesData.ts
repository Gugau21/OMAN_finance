import type { Candle, StockCandles } from '../types/stock';

import { getConfiguredDataSource, type DataSource, type MockScenario } from './dataSource';
import { parseStockCandles } from './responseValidation';
import { getJson } from '../lib/http/client';
import { HttpError } from '../lib/http/errors';

export type StockCandlesOptions = {
  source?: DataSource;
  scenario?: MockScenario;
  signal?: AbortSignal;
};

function createMockCandles(symbol: string): StockCandles {
  const candles: Candle[] = Array.from({ length: 60 }, (_, index) => {
    const close = Number((230 + index * 0.024).toFixed(2));

    return {
      timestamp: new Date(Date.UTC(2026, 9, 29, 16, index)).toISOString(),
      open: close - 0.1,
      high: close + 0.15,
      low: close - 0.2,
      close,
      volume: 30000 + index * 100,
    };
  });

  return { symbol, candles };
}

export async function getStockCandles(
  symbol: string,
  options: StockCandlesOptions = {},
): Promise<StockCandles | null> {
  const source = options.source ?? getConfiguredDataSource('stockCandles');

  if (source === 'mock') {
    return getMockStockCandles(symbol, options.scenario, options.signal);
  }

  try {
    const value = await getJson(
      `/api/stocks/${encodeURIComponent(symbol)}/candles`,
      {
        signal: options.signal,
        resourceName: 'stock candles',
      },
    );
    return parseStockCandles(value);
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

async function getMockStockCandles(
  symbol: string,
  scenario: MockScenario = getConfiguredScenario(),
  signal?: AbortSignal,
): Promise<StockCandles | null> {
  if (scenario === 'empty' || symbol !== 'AAPL') {
    return null;
  }

  if (scenario === 'error') {
    throw new Error('Stock candles mock request failed');
  }

  if (scenario === 'loading') {
    return waitForever(signal);
  }

  return createMockCandles(symbol);
}

function getConfiguredScenario(): MockScenario {
  const configuredScenario = import.meta.env.VITE_STOCK_CANDLES_SCENARIO;

  return configuredScenario === 'empty' ||
    configuredScenario === 'error' ||
    configuredScenario === 'loading'
    ? configuredScenario
    : 'success';
}

function waitForever(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Stock candles mock request aborted', 'AbortError'));
      return;
    }

    signal?.addEventListener(
      'abort',
      () => reject(new DOMException('Stock candles mock request aborted', 'AbortError')),
      { once: true },
    );
  });
}
