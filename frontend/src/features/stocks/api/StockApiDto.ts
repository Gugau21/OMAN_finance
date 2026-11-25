import { isRecord, isSymbol } from '../../../lib/validation/guards';
import type { components } from '../../../types/api.generated';
import { isPricePointDto, isStockStatDto } from '../dto/stockDtoGuards';

export type StockDto = components['schemas']['Stock'];

export type SparklineDto = components['schemas']['Sparkline'];

export type StockStatDto = components['schemas']['StockStat'];

export type CandleDto = components['schemas']['Candle'];
export type StockCandlesDto = components['schemas']['StockCandles'];

export function parseStocksDto(value: unknown): StockDto[] {
  if (!Array.isArray(value) || !value.every(isStocksDto)) {
    throw new Error('Stocks response has an invalid shape');
  }

  return value;
}

export function parseSparklinesDto(value: unknown): SparklineDto[] {
  if (!Array.isArray(value) || !value.every(isSparklineDto)) {
    throw new Error('Sparklines response has an invalid shape');
  }

  return value;
}

export function parseStockStatsDto(value: unknown): StockStatDto[] {
  if (!Array.isArray(value) || !value.every(isStockStatDto)) {
    throw new Error('Sparklines response has an invalid shape');
  }

  return value;
}

function isStocksDto(value: unknown): value is StockDto {
  return isRecord(value) && isSymbol(value.symbol) && typeof value.name === 'string';
}

function isSparklineDto(value: unknown): value is SparklineDto {
  return (
    isRecord(value) &&
    isSymbol(value.symbol) &&
    Array.isArray(value.points) &&
    value.points.every(isPricePointDto)
  );
}
