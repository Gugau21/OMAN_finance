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
    isRecord(value)
  );
}

export function isStockCandlesDto(value: unknown): value is StockCandlesDto {
  return (
    isRecord(value) &&
    isSymbol(value.symbol) &&
    Array.isArray(value.candles) &&
    value.candles.every(isCandleDto)
  );
}
