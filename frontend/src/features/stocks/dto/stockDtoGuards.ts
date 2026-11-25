import {
  isFiniteNumber,
  isNonNegativeInteger,
  isRecord,
  isRfc3339DateTime,
  isSymbol,
} from '../../../lib/validation/guards';
import type { components } from '../../../types/api.generated';

type PricePointDto = components['schemas']['PricePoint'];
type StockStatDto = components['schemas']['StockStat'];

export function isPricePointDto(value: unknown): value is PricePointDto {
  return isRecord(value) && isRfc3339DateTime(value.timestamp) && isFiniteNumber(value.price);
}

export function isStockStatDto(value: unknown): value is StockStatDto {
  return (
    isRecord(value) &&
    isSymbol(value.symbol) &&
    isRfc3339DateTime(value.timestamp) &&
    isFiniteNumber(value.price) &&
    isFiniteNumber(value.change) &&
    isFiniteNumber(value.changePercent) &&
    isNonNegativeInteger(value.volume)
  );
}
