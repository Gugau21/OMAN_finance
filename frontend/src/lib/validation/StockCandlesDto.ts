import {
  isFiniteNumber,
  isNonNegativeInteger,
  isRecord,
  isRfc3339DateTime,
  isSymbol,
} from './guards';
import type { components } from '../../types/api.generated';

export type CandleDto = components['schemas']['Candle'];

export type StockCandlesDto = components['schemas']['StockCandles'];

export function isCandleDto(value: unknown): value is CandleDto {
  return (
    isRecord(value) &&
    isRfc3339DateTime(value.timestamp) &&
    isFiniteNumber(value.open) &&
    isFiniteNumber(value.high) &&
    isFiniteNumber(value.low) &&
    isFiniteNumber(value.close) &&
    isNonNegativeInteger(value.volume)
  );
}

export function isStockCandlesDto(value: unknown): value is StockCandlesDto {
  return (
    isRecord(value) &&
    isSymbol(value.symbol) &&
    Array.isArray(value.points) &&
    value.points.every(isCandleDto)
  );
}
