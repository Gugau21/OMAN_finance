import { describe, expect, it, vi } from 'vitest';

import { getStockPageData } from './stockPageData';
import { getMockStockCandles, getMockStockStat } from '../features/stocks/mocks/stockMocks';

vi.mock('./dataSource', () => ({
  getConfiguredDataSource: () => 'mock',
}));
vi.mock('../features/stocks/mocks/stockMocks', () => ({
  getMockStockCandles: vi.fn(),
  getMockStockStat: vi.fn(),
}));

describe('getStockPageData', () => {
  it('fetches and combines stock stat and candles', async () => {
    const signal = new AbortController().signal;
    const statDto = {
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:07:00Z',
      price: 231.42,
      change: 1.87,
      changePercent: 0.81,
      volume: 1843200,
    };
    const candle = {
      timestamp: '2026-10-29T17:07:00Z',
      open: 231.1,
      high: 231.55,
      low: 231.02,
      close: 231.42,
      volume: 31250,
    };
    const candlesDto = {
      symbol: 'AAPL',
      candles: Array.from({ length: 60 }, () => candle),
    };

    vi.mocked(getMockStockStat).mockResolvedValue(statDto);
    vi.mocked(getMockStockCandles).mockResolvedValue(candlesDto);

    await expect(getStockPageData('AAPL', signal)).resolves.toEqual({
      candles: {
        symbol: 'AAPL',
        candles: Array.from({ length: 60 }, () => ({
          ...candle,
          timestamp: new Date(candle.timestamp),
        })),
      },
      stat: { ...statDto, timestamp: new Date(statDto.timestamp) },
      sparkline: Array.from({ length: 60 }, () => ({
        timestamp: new Date(candle.timestamp),
        price: 231.42,
        volume: 31250,
      })),
    });

    expect(getMockStockStat).toHaveBeenCalledWith('AAPL', undefined, signal);
    expect(getMockStockCandles).toHaveBeenCalledWith('AAPL', undefined, signal);
  });

  it('returns null when the stock stat is unavailable', async () => {
    vi.mocked(getMockStockStat).mockResolvedValue(null);
    vi.mocked(getMockStockCandles).mockResolvedValue({
      symbol: 'AAPL',
      candles: Array.from({ length: 60 }, () => ({
        timestamp: '2026-10-29T17:07:00Z',
        open: 231.1,
        high: 231.55,
        low: 231.02,
        close: 231.42,
        volume: 31250,
      })),
    });

    await expect(getStockPageData('AAPL')).resolves.toBeNull();
  });

  it('returns null when candles are unavailable', async () => {
    vi.mocked(getMockStockStat).mockResolvedValue({
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:07:00Z',
      price: 231.42,
      change: 1.87,
      changePercent: 0.81,
      volume: 1843200,
    });
    vi.mocked(getMockStockCandles).mockResolvedValue(null);

    await expect(getStockPageData('AAPL')).resolves.toBeNull();
  });
});
