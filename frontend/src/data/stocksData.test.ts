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

  it('returns null for the empty mock scenario', async () => {
    await expect(getStocks({ source: 'mock', scenario: 'empty' })).resolves.toBeNull();
  });

  it('throws for the error mock scenario', async () => {
    await expect(getStocks({ source: 'mock', scenario: 'error' })).rejects.toThrow(
      'Stocks mock request failed',
    );
  });

  it('stays pending for the loading mock scenario until aborted', async () => {
    const controller = new AbortController();
    const request = getStocks({
      source: 'mock',
      scenario: 'loading',
      signal: controller.signal,
    });

    let settled = false;
    void request.then(
      () => {
        settled = true;
      },
      () => {
        settled = true;
      },
    );

    await Promise.resolve();
    expect(settled).toBe(false);

    controller.abort();

    await expect(request).rejects.toThrow('Stocks mock request aborted');
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

  it('throws when real data has an invalid shape', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(new Response(JSON.stringify([{ symbol: 'AAPL' }]), { status: 200 })),
    );

    await expect(getStocks('real')).rejects.toThrow('Stocks response has an invalid shape');
  });
});
