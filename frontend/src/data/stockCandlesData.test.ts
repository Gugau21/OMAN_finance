import { afterEach, describe, expect, it, vi } from 'vitest';

import { getStockCandles } from './stockCandlesData';

function createCandles() {
  return Array.from({ length: 60 }, (_, index) => ({
    timestamp: new Date(Date.UTC(2026, 9, 29, 16, index)).toISOString(),
    open: 230,
    high: 231,
    low: 229,
    close: 230.5,
    volume: 30000,
  }));
}

describe('getStockCandles', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns 60 mock candles for a known symbol', async () => {
    const result = await getStockCandles('AAPL', { source: 'mock' });

    expect(result?.symbol).toBe('AAPL');
    expect(result?.candles).toHaveLength(60);
  });

  it('supports mock empty and error scenarios', async () => {
    await expect(
      getStockCandles('AAPL', { source: 'mock', scenario: 'empty' }),
    ).resolves.toBeNull();
    await expect(
      getStockCandles('AAPL', { source: 'mock', scenario: 'error' }),
    ).rejects.toThrow('Stock candles mock request failed');
  });

  it('supports aborting the mock loading scenario', async () => {
    const controller = new AbortController();
    const request = getStockCandles('AAPL', {
      source: 'mock',
      scenario: 'loading',
      signal: controller.signal,
    });

    controller.abort();

    await expect(request).rejects.toMatchObject({ name: 'AbortError' });
  });

  it('fetches real stock candles', async () => {
    const stockCandles = { symbol: 'MSFT', candles: createCandles() };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify(stockCandles), { status: 200 })),
    );

    await expect(getStockCandles('MSFT', { source: 'real' })).resolves.toEqual(stockCandles);
    expect(fetch).toHaveBeenCalledWith('/api/stocks/MSFT/candles');
  });

  it('returns null for a real 404 response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 404 })));

    await expect(getStockCandles('UNKNOWN', { source: 'real' })).resolves.toBeNull();
  });
});
