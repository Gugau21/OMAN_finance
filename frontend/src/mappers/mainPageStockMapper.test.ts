import { describe, expect, it } from 'vitest';

import { createMainPageStocks } from './mainPageStockMapper';

describe('createMainPageStocks', () => {
  it('combines stock, stat, and sparkline data by symbol', () => {
    const pricePoints = [{ timestamp: '2026-10-29T17:07:00Z', price: 231.42 }];

    expect(
      createMainPageStocks(
        [{ symbol: 'AAPL', name: 'Apple Inc.' }],
        [
          {
            symbol: 'AAPL',
            timestamp: '2026-10-29T17:07:00Z',
            price: 231.42,
            change: 1.87,
            changePercent: 0.81,
            volume: 1843200,
          },
        ],
        [{ symbol: 'AAPL', points: pricePoints }],
      ),
    ).toEqual([
      {
        symbol: 'AAPL',
        name: 'Apple Inc.',
        timestamp: '2026-10-29T17:07:00Z',
        price: 231.42,
        change: 1.87,
        changePercent: 0.81,
        volume: 1843200,
        pricePoints,
      },
    ]);
  });

  it('excludes stocks without matching stats or sparklines', () => {
    expect(
      createMainPageStocks(
        [
          { symbol: 'AAPL', name: 'Apple Inc.' },
          { symbol: 'MSFT', name: 'Microsoft Corporation' },
        ],
        [
          {
            symbol: 'AAPL',
            timestamp: '2026-10-29T17:07:00Z',
            price: 231.42,
            change: 1.87,
            changePercent: 0.81,
            volume: 1843200,
          },
        ],
        [],
      ),
    ).toEqual([]);
  });

  it('returns an empty array when there are no stocks', () => {
    expect(createMainPageStocks([], [], [])).toEqual([]);
  });
});
