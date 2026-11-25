import { describe, expect, it } from 'vitest';
import { toMarketStatus } from '../mappers/marketMapper';

describe('toMarketStatus', () => {
  it('converts DTO timestamp strings into Dates in the UI model', () => {
    const result = toMarketStatus({
      timestamp: '2026-10-29T13:08:00-04:00',
      isOpen: true,
      nextOpen: '2026-10-30T09:30:00-04:00',
      nextClose: '2026-10-29T16:00:00-04:00',
    });

    expect(result).toEqual({
      timestamp: new Date('2026-10-29T13:08:00-04:00'),
      isOpen: true,
      nextOpen: new Date('2026-10-30T09:30:00-04:00'),
      nextClose: new Date('2026-10-29T16:00:00-04:00'),
    });
  });
});
