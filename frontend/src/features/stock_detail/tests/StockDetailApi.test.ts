import { afterEach, describe, expect, it, vi } from 'vitest';
import { getStockCandles, getStockStatResponse } from '../api/StockDetailApi';

describe('stock detail API', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('returns null for 404 responses', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 404, statusText: 'Not Found' }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(getStockCandles('UNKNOWN', 'real')).resolves.toBeNull();
    await expect(getStockStatResponse('UNKNOWN', 'real')).resolves.toBeNull();
  });

  it('rethrows non-404 HTTP errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(new Response(null, { status: 503, statusText: 'Service Unavailable' })),
    );

    await expect(getStockCandles('AAPL', 'real')).rejects.toThrow(
      'Failed to fetch stock candles: 503 Service Unavailable',
    );
    await expect(getStockStatResponse('AAPL', 'real')).rejects.toThrow(
      'Failed to fetch stock stat: 503 Service Unavailable',
    );
  });
});
