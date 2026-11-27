import { isRecord, isRfc3339DateTime } from './guards';
import type { components } from '../../types/api.generated';

export type MarketStatusDto = components['schemas']['MarketStatus'];

export function isMarketStatusDto(value: unknown): value is MarketStatusDto {
  return (
    isRecord(value) &&
    isRfc3339DateTime(value.timestamp) &&
    typeof value.isOpen === 'boolean' &&
    isRfc3339DateTime(value.nextOpen) &&
    isRfc3339DateTime(value.nextClose)
  );
}
