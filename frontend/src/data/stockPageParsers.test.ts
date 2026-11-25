import { describe, expect, it } from 'vitest';

import { parseStockCandles, parseStockStat } from './stockPageParsers';

describe('stock page parsers', () => {
  it('converts stock stat timestamps to Dates', () => {
    const result = parseStockStat({
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:07:00Z',
      price: 231.42,
      change: 1.87,
      changePercent: 0.81,
      volume: 1843200,
    });

    expect(result.timestamp).toEqual(new Date('2026-10-29T17:07:00Z'));
    expect(result.price).toBe(231.42);
  });

  it('converts candle timestamps to chart model Dates', () => {
    const timestamp = '2026-10-29T17:07:00Z';
    const candle = {
      timestamp,
      open: 231.1,
      high: 231.55,
      low: 231.02,
      close: 231.42,
      volume: 31250,
    };
    const result = parseStockCandles({
      symbol: 'AAPL',
      candles: Array.from({ length: 60 }, () => candle),
    });

    expect(result.candles[0]).toEqual({
      ...candle,
      timestamp: new Date(timestamp),
    });
  });
});
