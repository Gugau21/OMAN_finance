import type {
  Sparkline,
  Stock,
  StockCandles,
  StockStat,
} from '../model/stock';
import type { ChartCandle } from '../../../components/charts/model/chartTypes';
import { getConfiguredDataSource, type DataSource, type MockScenario } from '../../../data/dataSource';
import { HttpError } from '../../../lib/http/errors';
import { getJson } from '../../../lib/http/client';
import {
  parseSparklines,
  parseStockCandles,
  parseStocks,
  parseStockStat,
  parseStockStats,
} from '../mappers/stockMapper';
import { createStockList } from '../mappers/stockListMapper';
import type { StockListItem } from '../model/stock';

export async function getStockList(signal?: AbortSignal): Promise<StockListItem[] | null> {
  const [stocks, stats, sparklines] = await Promise.all([
    getStocks({ signal }),
    getStockStats({ signal }),
    getSparklines({ signal }),
  ]);

  if (!stocks || !stats || !sparklines) {
    return null;
  }

  return createStockList(stocks, stats, sparklines);
}

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

  const value = await getJson('/api/stocks', {
    signal: options.signal,
    resourceName: 'stocks',
  });
  return parseStocks(value);
}

export async function getStockStats(
  sourceOrOptions: DataSource | StocksOptions = {},
): Promise<StockStat[] | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('stockStats');

  if (source === 'mock') {
    return getMockStockStats(options.scenario, options.signal);
  }

  const value = await getJson('/api/stocks/stat', {
    signal: options.signal,
    resourceName: 'stock stats',
  });
  return parseStockStats(value);
}

export async function getSparklines(
  sourceOrOptions: DataSource | StocksOptions = {},
): Promise<Sparkline[] | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('sparklines');

  if (source === 'mock') {
    return getMockSparklines(options.scenario, options.signal);
  }

  const value = await getJson('/api/stocks/sparkline', {
    signal: options.signal,
    resourceName: 'sparklines',
  });
  return parseSparklines(value);
}

export type StockStatOptions = StocksOptions;

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

export type StockCandlesOptions = StocksOptions;

export async function getStockCandles(
  symbol: string,
  options: StockCandlesOptions = {},
): Promise<StockCandles | null> {
  const source = options.source ?? getConfiguredDataSource('stockCandles');

  if (source === 'mock') {
    return getMockStockCandles(symbol, options.scenario, options.signal);
  }

  try {
    const value = await getJson(`/api/stocks/${encodeURIComponent(symbol)}/candles`, {
      signal: options.signal,
      resourceName: 'stock candles',
    });
    return parseStockCandles(value);
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

const mockStockStats: StockStat[] = [
  {
    symbol: 'AAPL',
    timestamp: new Date('2026-10-29T17:07:00Z'),
    price: 231.42,
    change: 1.87,
    changePercent: 0.81,
    volume: 1843200,
  },
  {
    symbol: 'MSFT',
    timestamp: new Date('2026-10-29T17:07:00Z'),
    price: 420.1,
    change: -2.35,
    changePercent: -0.56,
    volume: 1264800,
  },
  {
    symbol: 'NVDA',
    timestamp: new Date('2026-10-29T17:07:00Z'),
    price: 145.75,
    change: 3.12,
    changePercent: 2.19,
    volume: 2389100,
  },
];

const mockStockStat: StockStat = mockStockStats[0];

function getMockStocks(
  scenario: MockScenario = getConfiguredScenario('STOCKS'),
  signal?: AbortSignal,
): Promise<Stock[] | null> {
  if (scenario === 'empty') return Promise.resolve(null);
  if (scenario === 'error') return Promise.reject(new Error('Stocks mock request failed'));
  if (scenario === 'loading') return waitForever(signal);
  return Promise.resolve(mockStocks);
}

function getMockStockStats(
  scenario: MockScenario = getConfiguredScenario('STOCK_STATS'),
  signal?: AbortSignal,
): Promise<StockStat[] | null> {
  if (scenario === 'empty') return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Stock stats mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);
  return Promise.resolve(mockStockStats);
}

function getMockSparklines(
  scenario: MockScenario = getConfiguredScenario('SPARKLINES'),
  signal?: AbortSignal,
): Promise<Sparkline[] | null> {
  if (scenario === 'empty') return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Sparklines mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);

  const sparklines = [
    createMockSparkline('AAPL', 231.42),
    createMockSparkline('MSFT', 420.1),
    createMockSparkline('NVDA', 145.75),
  ];
  return Promise.resolve(sparklines);
}

function getMockStockStat(
  symbol: string,
  scenario: MockScenario = getConfiguredScenario('STOCK_STAT'),
  signal?: AbortSignal,
): Promise<StockStat | null> {
  if (scenario === 'empty' || symbol !== mockStockStat.symbol) return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Stock stat mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);
  return Promise.resolve({ ...mockStockStat });
}

function getMockStockCandles(
  symbol: string,
  scenario: MockScenario = getConfiguredScenario('STOCK_CANDLES'),
  signal?: AbortSignal,
): Promise<StockCandles | null> {
  if (scenario === 'empty' || symbol !== 'AAPL') return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Stock candles mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);

  const candles: ChartCandle[] = Array.from({ length: 60 }, (_, index) => {
    const close = Number((230 + index * 0.024).toFixed(2));
    return {
      timestamp: new Date(Date.UTC(2026, 9, 29, 16, index)),
      open: close - 0.1,
      high: close + 0.15,
      low: close - 0.2,
      close,
      volume: 30000 + index * 100,
    };
  });

  return Promise.resolve({ symbol, candles });
}

function createMockSparkline(symbol: string, lastPrice: number): Sparkline {
  const start = Date.parse('2026-10-29T16:08:00Z');
  const points = Array.from({ length: 60 }, (_, index) => ({
    timestamp: new Date(start + index * 60_000),
    price: Number((lastPrice - (59 - index) * 0.08).toFixed(2)),
  }));
  return { symbol, points };
}

function getConfiguredScenario(name: string): MockScenario {
  const scenario =
    name === 'STOCKS'
      ? import.meta.env.VITE_STOCKS_SCENARIO
      : name === 'STOCK_STATS'
        ? import.meta.env.VITE_STOCK_STATS_SCENARIO
        : name === 'SPARKLINES'
          ? import.meta.env.VITE_SPARKLINES_SCENARIO
          : name === 'STOCK_STAT'
            ? import.meta.env.VITE_STOCK_STAT_SCENARIO
            : import.meta.env.VITE_STOCK_CANDLES_SCENARIO;

  return scenario === 'empty' || scenario === 'error' || scenario === 'loading'
    ? scenario
    : 'success';
}

function waitForever(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Stocks mock request aborted', 'AbortError'));
      return;
    }

    signal?.addEventListener(
      'abort',
      () => reject(new DOMException('Stocks mock request aborted', 'AbortError')),
      { once: true },
    );
  });
}
