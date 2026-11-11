import { describe, expect, it, vi } from 'vitest';

import { getMainPageStocks } from './mainPageData';
import { getSparklines } from './sparklinesData';
import { getStocks } from './stocksData';
import { getStockStats } from './stockStatsData';

vi.mock('./sparklinesData');
vi.mock('./stocksData');
vi.mock('./stockStatsData');

describe('getMainPageStocks', () => {
  it('fetches and combines all main-page data', async () => {
    vi.mocked(getStocks).mockResolvedValue([{ symbol: 'AAPL', name: 'Apple Inc.' }]);
    vi.mocked(getStockStats).mockResolvedValue([
      {
        symbol: 'AAPL',
        timestamp: '2026-10-29T17:07:00Z',
        price: 231.42,
        change: 1.87,
        changePercent: 0.81,
        volume: 1843200,
      },
    ]);
    vi.mocked(getSparklines).mockResolvedValue([
      {
        symbol: 'AAPL',
        points: [{ timestamp: '2026-10-29T17:07:00Z', price: 231.42 }],
      },
    ]);

    await expect(getMainPageStocks()).resolves.toEqual([
      {
        symbol: 'AAPL',
        name: 'Apple Inc.',
        timestamp: '2026-10-29T17:07:00Z',
        price: 231.42,
        change: 1.87,
        changePercent: 0.81,
        volume: 1843200,
        pricePoints: [{ timestamp: '2026-10-29T17:07:00Z', price: 231.42 }],
      },
    ]);
  });

  it('returns null when one endpoint has no data', async () => {
    vi.mocked(getStocks).mockResolvedValue(null);
    vi.mocked(getStockStats).mockResolvedValue([]);
    vi.mocked(getSparklines).mockResolvedValue([]);

    await expect(getMainPageStocks()).resolves.toBeNull();
  });

  it('passes the abort signal to every endpoint', async () => {
    const controller = new AbortController();
    vi.mocked(getStocks).mockResolvedValue([]);
    vi.mocked(getStockStats).mockResolvedValue([]);
    vi.mocked(getSparklines).mockResolvedValue([]);

    await getMainPageStocks(controller.signal);

    expect(getStocks).toHaveBeenCalledWith({ signal: controller.signal });
    expect(getStockStats).toHaveBeenCalledWith({ signal: controller.signal });
    expect(getSparklines).toHaveBeenCalledWith({ signal: controller.signal });
  });
});
