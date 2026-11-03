import { afterEach, describe, expect, it, vi } from 'vitest';

import { getMarketStatus } from './marketData';

describe('getMarketStatus', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns mock data by default', async () => {
    await expect(getMarketStatus('mock')).resolves.toEqual({
      timestamp: '2026-10-29T13:08:00-04:00',
      isOpen: true,
      nextOpen: '2026-10-30T09:30:00-04:00',
      nextClose: '2026-10-29T16:00:00-04:00',
    });
  });

  it('returns real data when requested', async () => {
    const response = {
      timestamp: '2026-10-29T13:08:00-04:00',
      isOpen: false,
      nextOpen: '2026-10-30T09:30:00-04:00',
      nextClose: '2026-10-30T16:00:00-04:00',
    };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify(response), { status: 200 })),
    );

    await expect(getMarketStatus('real')).resolves.toEqual(response);
    expect(fetch).toHaveBeenCalledWith('/api/market/status');
  });

  it('throws when real data cannot be loaded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(null, {
          status: 503,
          statusText: 'Service Unavailable',
        }),
      ),
    );

    await expect(getMarketStatus('real')).rejects.toThrow(
      'Failed to fetch market status: 503 Service Unavailable',
    );
  });
});
