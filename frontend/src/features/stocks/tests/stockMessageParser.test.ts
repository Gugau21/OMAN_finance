import { describe, expect, it } from 'vitest';
import { parseSparklinePointMessage, parseStockStatMessage } from '../realtime/stockMessageParser';

describe('stock WebSocket message parsers', () => {
  it('parses a sparkline point message', () => {
    expect(
      parseSparklinePointMessage({
        symbol: 'AAPL',
        timestamp: '2026-10-29T17:07:00Z',
        price: 231.42,
      }),
    ).toEqual({
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:07:00Z',
      price: 231.42,
    });
  });

  it('rejects an invalid sparkline point message', () => {
    expect(() =>
      parseSparklinePointMessage({
        timestamp: '2026-10-29T17:07:00Z',
        price: 231.42,
      }),
    ).toThrow('Sparkline point message has an invalid shape');
  });

  it('parses stock stat messages with negative change percentages', () => {
    expect(
      parseStockStatMessage({
        symbol: 'MSFT',
        timestamp: '2026-10-29T17:07:00Z',
        price: 420.1,
        change: -2.35,
        changePercent: -0.56,
        volume: 1264800,
      }),
    ).toMatchObject({
      symbol: 'MSFT',
      changePercent: -0.56,
    });
  });

  it('rejects an invalid stock stat message', () => {
    expect(() =>
      parseStockStatMessage({
        symbol: 'MSFT',
        timestamp: 'not-a-timestamp',
      }),
    ).toThrow('Stock stat message has an invalid shape');
  });
});
