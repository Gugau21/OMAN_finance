import { afterEach, describe, expect, it, vi } from 'vitest';

import { getStocks } from './stocksData';

describe('getStocks', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns mock stocks when requested', async () => {
    await expect(getStocks('mock')).resolves.toEqual([
      { symbol: 'AAPL', name: 'Apple Inc.' },
      { symbol: 'MSFT', name: 'Microsoft Corporation' },
      { symbol: 'NVDA', name: 'NVIDIA Corporation' },
    ]);
  });

  it('returns real stocks when requested', async () => {
    const stocks = [{ symbol: 'AAPL', name: 'Apple Inc.' }];

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify(stocks), { status: 200 })),
    );

    await expect(getStocks('real')).resolves.toEqual(stocks);
    expect(fetch).toHaveBeenCalledWith('/api/stocks');
  });

  it('throws when real stocks cannot be loaded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(null, {
          status: 503,
          statusText: 'Service Unavailable',
        }),
      ),
    );

    await expect(getStocks('real')).rejects.toThrow(
      'Failed to fetch stocks: 503 Service Unavailable',
    );
  });
});
