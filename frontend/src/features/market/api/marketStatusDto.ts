import { isRecord, isRfc3339DateTime } from '../../../lib/validation/guards';
import type { components } from '../../../types/api.generated';

export type MarketStatusDto = components['schemas']['MarketStatus'];

export function parseMarketStatusDto(value: unknown): MarketStatusDto {
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
    timestamp: value.timestamp,
    isOpen: value.isOpen,
    nextOpen: value.nextOpen,
    nextClose: value.nextClose,
  };
}
