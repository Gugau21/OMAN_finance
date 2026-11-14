import { afterEach, describe, expect, it, vi } from 'vitest';

import { getStockStat } from './stockStatData';

describe('getStockStat', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns mock data for a known symbol', async () => {
    await expect(
      getStockStat('AAPL', { source: 'mock', scenario: 'success' }),
    ).resolves.toMatchObject({
      symbol: 'AAPL',
      price: 231.42,
    });
  });

  it('returns null for an unknown mock symbol', async () => {
    await expect(getStockStat('UNKNOWN', { source: 'mock' })).resolves.toBeNull();
  });

  it('supports mock empty and error scenarios', async () => {
    await expect(
      getStockStat('AAPL', { source: 'mock', scenario: 'empty' }),
    ).resolves.toBeNull();
    await expect(
      getStockStat('AAPL', { source: 'mock', scenario: 'error' }),
    ).rejects.toThrow('Stock stat mock request failed');
  });

  it('supports aborting the mock loading scenario', async () => {
    const controller = new AbortController();
    const request = getStockStat('AAPL', {
      source: 'mock',
      scenario: 'loading',
      signal: controller.signal,
    });

    controller.abort();

    await expect(request).rejects.toMatchObject({ name: 'AbortError' });
  });

  it('fetches a real stock stat', async () => {
    const stockStat = {
      symbol: 'MSFT',
      timestamp: '2026-10-29T17:07:00Z',
      price: 420.1,
      change: -2.35,
      changePercent: -0.56,
      volume: 1264800,
    };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify(stockStat), { status: 200 })),
    );

    await expect(getStockStat('MSFT', { source: 'real' })).resolves.toEqual(stockStat);
    expect(fetch).toHaveBeenCalledWith('/api/stocks/MSFT/stat');
  });

  it('returns null for a real 404 response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 404 })));

    await expect(getStockStat('UNKNOWN', { source: 'real' })).resolves.toBeNull();
  });
});
