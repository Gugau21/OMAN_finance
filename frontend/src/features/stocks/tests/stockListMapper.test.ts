import { describe, expect, it } from 'vitest';

import {
  mergeSparklinePointMessage,
  mergeStockStatMessage,
  toStockListItem,
} from '../mappers/stockListMapper';

const timestamp = '2026-10-29T17:07:00Z';

function createStockList() {
  return toStockListItem(
    [{ symbol: 'AAPL', name: 'Apple Inc.' }],
    [
      {
        symbol: 'AAPL',
        timestamp,
        price: 231.42,
        change: 1.87,
        changePercent: 0.81,
        volume: 1843200,
      },
    ],
    [
      {
        symbol: 'AAPL',
        points: [{ timestamp, price: 231.42 }],
      },
    ],
  );
}

describe('toStockListItem', () => {
  it('combines stock, stat, and sparkline DTOs by symbol', () => {
    expect(createStockList()).toEqual([
      {
        symbol: 'AAPL',
        name: 'Apple Inc.',
        stat: {
          timestamp: new Date(timestamp),
          price: 231.42,
          change: 1.87,
          changePercent: 0.81,
          volume: 1843200,
        },
        sparkline: {
          points: [{ timestamp: new Date(timestamp), price: 231.42 }],
        },
      },
    ]);
  });

  it('excludes stocks without matching stats or sparklines', () => {
    expect(
      toStockListItem(
        [
          { symbol: 'AAPL', name: 'Apple Inc.' },
          { symbol: 'MSFT', name: 'Microsoft Corporation' },
        ],
        [],
        [],
      ),
    ).toEqual([]);
  });

  it('returns an empty array when there are no stocks', () => {
    expect(toStockListItem([], [], [])).toEqual([]);
  });
});

describe('stock realtime mappers', () => {
  it('replaces a stock stat with a newer message', () => {
    const stocks = createStockList();
    const updated = mergeStockStatMessage(stocks, {
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:08:00Z',
      price: 232,
      change: 2,
      changePercent: 0.9,
      volume: 1900000,
    });

    expect(updated?.[0].stat).toEqual({
      timestamp: new Date('2026-10-29T17:08:00Z'),
      price: 232,
      change: 2,
      changePercent: 0.9,
      volume: 1900000,
    });
  });

  it('appends a newer sparkline point and drops the oldest beyond 60 points', () => {
    const points = Array.from({ length: 60 }, (_, index) => {
      const pointTimestamp = new Date(Date.parse(timestamp) + index * 60_000).toISOString();
      return { timestamp: pointTimestamp, price: index };
    });
    const stocks = toStockListItem(
      [{ symbol: 'AAPL', name: 'Apple Inc.' }],
      [
        {
          symbol: 'AAPL',
          timestamp,
          price: 231.42,
          change: 1.87,
          changePercent: 0.81,
          volume: 1843200,
        },
      ],
      [{ symbol: 'AAPL', points }],
    );
    const updated = mergeSparklinePointMessage(stocks, {
      symbol: 'AAPL',
      timestamp: new Date(Date.parse(timestamp) + 60 * 60_000).toISOString(),
      price: 232,
    });

    expect(updated?.[0].sparkline.points).toHaveLength(60);
    expect(updated?.[0].sparkline.points[0]).toEqual({
      timestamp: new Date(Date.parse(timestamp) + 60_000),
      price: 1,
    });
    expect(updated?.[0].sparkline.points[59]).toEqual({
      timestamp: new Date(Date.parse(timestamp) + 60 * 60_000),
      price: 232,
    });
  });

  it('ignores a sparkline point older than the current snapshot', () => {
    const stocks = createStockList();
    const updated = mergeSparklinePointMessage(stocks, {
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:06:00Z',
      price: 230,
    });

    expect(updated).toEqual(stocks);
  });
});
