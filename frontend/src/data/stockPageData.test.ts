import { describe, expect, it, vi } from 'vitest';

import { getStockPageData } from './stockPageData';
import { getStockCandles } from './stockCandlesData';
import { getStockStat } from './stockStatData';

vi.mock('./stockCandlesData');
vi.mock('./stockStatData');

describe('getStockPageData', () => {
  it('fetches and combines stock stat and candles', async () => {
    const signal = new AbortController().signal;
    const stat = {
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:07:00Z',
      price: 231.42,
      change: 1.87,
      changePercent: 0.81,
      volume: 1843200,
    };
    const candles = {
      symbol: 'AAPL',
      candles: [
        {
          timestamp: '2026-10-29T17:07:00Z',
          open: 231.1,
          high: 231.55,
          low: 231.02,
          close: 231.42,
          volume: 31250,
        },
      ],
    };

    vi.mocked(getStockStat).mockResolvedValue(stat);
    vi.mocked(getStockCandles).mockResolvedValue(candles);

    await expect(getStockPageData('AAPL', signal)).resolves.toEqual({
      candles,
      stat,
      sparkline: [
        {
          timestamp: '2026-10-29T17:07:00Z',
          price: 231.42,
          volume: 31250,
        },
      ],
    });

    expect(getStockStat).toHaveBeenCalledWith('AAPL', { signal });
    expect(getStockCandles).toHaveBeenCalledWith('AAPL', { signal });
  });

  it('returns null when the stock stat is unavailable', async () => {
    vi.mocked(getStockStat).mockResolvedValue(null);
    vi.mocked(getStockCandles).mockResolvedValue({
      symbol: 'AAPL',
      candles: [],
    });

    await expect(getStockPageData('AAPL')).resolves.toBeNull();
  });

  it('returns null when candles are unavailable', async () => {
    vi.mocked(getStockStat).mockResolvedValue({
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:07:00Z',
      price: 231.42,
      change: 1.87,
      changePercent: 0.81,
      volume: 1843200,
    });
    vi.mocked(getStockCandles).mockResolvedValue(null);

    await expect(getStockPageData('AAPL')).resolves.toBeNull();
  });
});
