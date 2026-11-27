import { type MockScenario, getConfiguredScenario, waitForever } from '../../../data/dataSource';
import type {
  ListSparklinesResponse,
  ListStockStatsResponse,
  ListStocksResponse,
} from '../api/StockApiDto';

const mockStocks: ListStocksResponse = [
  { symbol: 'AAPL', name: 'Apple Inc.' },
  { symbol: 'MSFT', name: 'Microsoft Corporation' },
  { symbol: 'NVDA', name: 'NVIDIA Corporation' },
];

const mockStockStats: ListStockStatsResponse = [
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

export function getMockStocks(
  scenario: MockScenario = getConfiguredScenario('stocks'),
  signal?: AbortSignal,
): Promise<ListStocksResponse | null> {
  if (scenario === 'empty') return Promise.resolve(null);
  if (scenario === 'error') return Promise.reject(new Error('Stocks mock request failed'));
  if (scenario === 'loading') return waitForever(signal);
  return Promise.resolve(mockStocks);
}

export function getMockStockStats(
  scenario: MockScenario = getConfiguredScenario('stockStats'),
  signal?: AbortSignal,
): Promise<ListStockStatsResponse | null> {
  if (scenario === 'empty') return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Stock stats mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);
  return Promise.resolve(mockStockStats);
}

export function getMockSparklines(
  scenario: MockScenario = getConfiguredScenario('sparklines'),
  signal?: AbortSignal,
): Promise<ListSparklinesResponse | null> {
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

function createMockSparkline(symbol: string, lastPrice: number) {
  const start = Date.parse('2026-10-29T16:08:00Z');
  const points = Array.from({ length: 60 }, (_, index) => ({
    timestamp: new Date(start + index * 60_000).toISOString(),
    price: Number((lastPrice - (59 - index) * 0.08).toFixed(2)),
  }));
  return { symbol, points };
}
