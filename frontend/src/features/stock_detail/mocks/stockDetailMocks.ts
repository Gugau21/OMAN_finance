import { type MockScenario, getConfiguredScenario, waitForever } from '../../../data/dataSource';
import type { GetStockCandlesResponse, GetStockStatResponse } from '../api/StockDetailApiDto';

const mockStockStats: GetStockStatResponse[] = [
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

const mockStockStat: GetStockStatResponse = mockStockStats[0];

export function getMockStockCandles(
  symbol: string,
  scenario: MockScenario = getConfiguredScenario('stockCandles'),
  signal?: AbortSignal,
): Promise<GetStockCandlesResponse | null> {
  if (scenario === 'empty' || symbol !== 'AAPL') return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Stock candles mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);

  const candles = Array.from({ length: 60 }, (_, index) => {
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

export function getMockStockStat(
  symbol: string,
  scenario: MockScenario = getConfiguredScenario('stockStat'),
  signal?: AbortSignal,
): Promise<GetStockStatResponse | null> {
  if (scenario === 'empty' || symbol !== mockStockStat.symbol) return Promise.resolve(null);
  if (scenario === 'error') {
    return Promise.reject(new Error('Stock stat mock request failed'));
  }
  if (scenario === 'loading') return waitForever(signal);
  return Promise.resolve({ ...mockStockStat });
}
