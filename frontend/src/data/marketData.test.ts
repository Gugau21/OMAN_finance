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

  it('returns null for the empty mock scenario', async () => {
    await expect(getMarketStatus({ source: 'mock', scenario: 'empty' })).resolves.toBeNull();
  });

  it('throws for the error mock scenario', async () => {
    await expect(getMarketStatus({ source: 'mock', scenario: 'error' })).rejects.toThrow(
      'Market status mock request failed',
    );
  });

  it('stays pending for the loading mock scenario until aborted', async () => {
    const controller = new AbortController();
    const request = getMarketStatus({
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

    await expect(request).rejects.toThrow('Market status mock request aborted');
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

  it('throws when real data has an invalid shape', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ isOpen: true }), { status: 200 })),
    );

    await expect(getMarketStatus('real')).rejects.toThrow(
      'Market status response has an invalid shape',
    );
  });
});
