import { isRecord, isSymbol } from './guards';
import type { components } from '../../types/api.generated';

export type StockDto = components['schemas']['Stock'];

export function isStockDto(value: unknown): value is StockDto {
  return isRecord(value) && isSymbol(value.symbol) && typeof value.name === 'string';
}
