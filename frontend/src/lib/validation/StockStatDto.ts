import {
  isFiniteNumber,
  isNonNegativeInteger,
  isRecord,
  isRfc3339DateTime,
  isSymbol,
} from './guards';
import type { components } from '../../types/api.generated';

export type StockStatDto = components['schemas']['StockStat'];

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
