import { afterEach, describe, expect, it, vi } from 'vitest';

import { getSparklines } from './sparklinesData';

describe('getSparklines', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns 60 points per stock for mock data', async () => {
    const sparklines = await getSparklines('mock');

    expect(sparklines).toHaveLength(3);
    expect(sparklines[0]?.symbol).toBe('AAPL');
    expect(sparklines[0]?.points).toHaveLength(60);
    expect(sparklines[0]?.points[0]?.price).toBe(226.7);
    expect(sparklines[0]?.points[59]?.price).toBe(231.42);
  });

  it('returns real data when requested', async () => {
    const sparklines = [
      {
        symbol: 'AAPL',
        points: Array.from({ length: 60 }, (_, index) => ({
          timestamp: new Date(Date.UTC(2026, 9, 29, 17, index)).toISOString(),
          price: 231.42,
        })),
      },
    ];

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify(sparklines), { status: 200 })),
    );

    await expect(getSparklines('real')).resolves.toEqual(sparklines);
    expect(fetch).toHaveBeenCalledWith('/api/stocks/sparkline');
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

    await expect(getSparklines('real')).rejects.toThrow(
      'Failed to fetch sparklines: 503 Service Unavailable',
    );
  });

  it('throws when real data has an invalid shape', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify([
            {
              symbol: 'AAPL',
              points: [{ timestamp: 'invalid', price: 231.42 }],
            },
          ]),
          { status: 200 },
        ),
      ),
    );

    await expect(getSparklines('real')).rejects.toThrow(
      'Sparklines response has an invalid shape',
    );
  });
});
