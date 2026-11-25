import type { MockScenario } from '../../../data/dataSource';
import type {
  CandleDto,
  SparklineDto,
  StockCandlesDto,
  StockDto,
  StockStatDto,
} from '../api/StockApiDto';

const mockStocks: StockDto[] = [
  { symbol: 'AAPL', name: 'Apple Inc.' },
  { symbol: 'MSFT', name: 'Microsoft Corporation' },
  { symbol: 'NVDA', name: 'NVIDIA Corporation' },
];

const mockStockStats: StockStatDto[] = [
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

const mockStockStat: StockStatDto = mockStockStats[0];

export function getMockStocks(
  scenario: MockScenario = getConfiguredScenario('STOCKS'),
  signal?: AbortSignal,
): Promise<StockDto[] | null> {
  if (scenario === 'empty') return Promise.resolve(null);
  if (scenario === 'error') return Promise.reject(new Error('Stocks mock request failed'));
  if (scenario === 'loading') return waitForever(signal);
  return Promise.resolve(mockStocks);
}

export function getMockStockStats(
  scenario: MockScenario = getConfiguredScenario('STOCK_STATS'),
  signal?: AbortSignal,
): Promise<StockStatDto[] | null> {
  if (scenario === 'empty') return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Stock stats mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);
  return Promise.resolve(mockStockStats);
}

export function getMockSparklines(
  scenario: MockScenario = getConfiguredScenario('SPARKLINES'),
  signal?: AbortSignal,
): Promise<SparklineDto[] | null> {
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

export function getMockStockStat(
  symbol: string,
  scenario: MockScenario = getConfiguredScenario('STOCK_STAT'),
  signal?: AbortSignal,
): Promise<StockStatDto | null> {
  if (scenario === 'empty' || symbol !== mockStockStat.symbol) return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Stock stat mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);
  return Promise.resolve({ ...mockStockStat });
}

export function getMockStockCandles(
  symbol: string,
  scenario: MockScenario = getConfiguredScenario('STOCK_CANDLES'),
  signal?: AbortSignal,
): Promise<StockCandlesDto | null> {
  if (scenario === 'empty' || symbol !== 'AAPL') return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Stock candles mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);

  const candles: CandleDto[] = Array.from({ length: 60 }, (_, index) => {
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

  return Promise.resolve({ symbol, candles });
}

function createMockSparkline(symbol: string, lastPrice: number): SparklineDto {
  const start = Date.parse('2026-10-29T16:08:00Z');
  const points = Array.from({ length: 60 }, (_, index) => ({
    timestamp: new Date(start + index * 60_000).toISOString(),
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
