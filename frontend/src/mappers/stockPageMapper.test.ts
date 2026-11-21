import { describe, expect, it } from 'vitest';

import { createStockPageView } from './stockPageMapper';

describe('createStockPageView', () => {
  it('creates a stock sparkline from candle close prices and volumes', () => {
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
        {
          timestamp: '2026-10-29T17:08:00Z',
          open: 231.42,
          high: 231.8,
          low: 231.3,
          close: 231.7,
          volume: 28000,
        },
      ],
    };
    const stat = {
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:07:00Z',
      price: 231.42,
      change: 1.87,
      changePercent: 0.81,
      volume: 1843200,
    };

    const view = createStockPageView(candles, stat);

    expect(view.candles).toBe(candles);
    expect(view.stat).toBe(stat);
    expect(view.sparkline).toEqual([
      {
        timestamp: '2026-10-29T17:07:00Z',
        price: 231.42,
        volume: 31250,
      },
      {
        timestamp: '2026-10-29T17:08:00Z',
        price: 231.7,
        volume: 28000,
      },
    ]);
  });

  it('rejects candles and stats for different symbols', () => {
    expect(() =>
      createStockPageView(
        { symbol: 'AAPL', candles: [] },
        {
          symbol: 'MSFT',
          timestamp: '2026-10-29T17:07:00Z',
          price: 420.1,
          change: -2.35,
          changePercent: -0.56,
          volume: 1264800,
        },
      ),
    ).toThrow('Cannot create stock page view for AAPL and MSFT');
  });
});
