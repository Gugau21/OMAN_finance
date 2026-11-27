import { isFiniteNumber, isRecord, isRfc3339DateTime, isSymbol } from './guards';
import type { components } from '../../types/api.generated';

export type PricePointDto = components['schemas']['PricePoint'];
export type SparklineDto = components['schemas']['Sparkline'];

export function isPricePointDto(value: unknown): value is PricePointDto {
  return isRecord(value) && isRfc3339DateTime(value.timestamp) && isFiniteNumber(value.price);
}

export function isSparklineDto(value: unknown): value is SparklineDto {
  return (
    isRecord(value) &&
    isSymbol(value.symbol) &&
    Array.isArray(value.points) &&
    value.points.every(isPricePointDto)
  );
}
