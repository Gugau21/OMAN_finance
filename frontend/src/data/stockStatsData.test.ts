import { afterEach, describe, expect, it, vi } from 'vitest';

import { getStockStats } from './stockStatsData';

describe('getStockStats', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns mock stock stats when requested', async () => {
    const stockStats = await getStockStats('mock');

    expect(stockStats).toHaveLength(3);
    expect(stockStats[0]).toEqual({
      symbol: 'AAPL',
      timestamp: '2026-10-29T17:07:00Z',
      price: 231.42,
      change: 1.87,
      changePercent: 0.81,
      volume: 1843200,
    });
  });

  it('returns real stock stats when requested', async () => {
    const stockStats = [
      {
        symbol: 'AAPL',
        timestamp: '2026-10-29T17:07:00Z',
        price: 231.42,
        change: 1.87,
        changePercent: 0.81,
        volume: 1843200,
      },
    ];

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify(stockStats), { status: 200 })),
    );

    await expect(getStockStats('real')).resolves.toEqual(stockStats);
    expect(fetch).toHaveBeenCalledWith('/api/stocks/stat');
  });

  it('throws when real stock stats cannot be loaded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(null, {
          status: 503,
          statusText: 'Service Unavailable',
        }),
      ),
    );

    await expect(getStockStats('real')).rejects.toThrow(
      'Failed to fetch stock stats: 503 Service Unavailable',
    );
  });

  it('throws when real stock stats have an invalid shape', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify([
            {
              symbol: 'AAPL',
              timestamp: '2026-10-29T17:07:00Z',
              price: 231.42,
              change: 1.87,
              changePercent: 0.81,
              volume: '1843200',
            },
          ]),
          { status: 200 },
        ),
      ),
    );

    await expect(getStockStats('real')).rejects.toThrow(
      'Stock stats response has an invalid shape',
    );
  });
});
