import type { MarketStatus } from '../model/market';
import { isRecord, isRfc3339DateTime } from '../../../lib/validation/guards';

export function parseMarketStatus(value: unknown): MarketStatus {
  if (
    !isRecord(value) ||
    !isRfc3339DateTime(value.timestamp) ||
    typeof value.isOpen !== 'boolean' ||
    !isRfc3339DateTime(value.nextOpen) ||
    !isRfc3339DateTime(value.nextClose)
  ) {
    throw new Error('Market status response has an invalid shape');
  }

  return {
    timestamp: new Date(value.timestamp),
    isOpen: value.isOpen,
    nextOpen: new Date(value.nextOpen),
    nextClose: new Date(value.nextClose),
  };
}

