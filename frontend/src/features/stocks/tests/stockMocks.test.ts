import { describe, expect, it } from 'vitest';
import { getMockSparklines, getMockStockCandles, getMockStockStats } from '../mocks/stockMocks';

describe('stock mocks', () => {
  it('returns stock statistics with DTO string timestamps', async () => {
    const stats = await getMockStockStats('success');

    expect(stats).not.toBeNull();
    expect(typeof stats?.[0].timestamp).toBe('string');
  });

  it('returns sparklines with DTO string timestamps', async () => {
    const sparklines = await getMockSparklines('success');

    expect(sparklines).not.toBeNull();
    expect(typeof sparklines?.[0].points[0].timestamp).toBe('string');
  });

  it('returns candles with DTO string timestamps', async () => {
    const result = await getMockStockCandles('AAPL', 'success');

    expect(result).not.toBeNull();
    expect(typeof result?.candles[0].timestamp).toBe('string');
  });
});
