import { isSparklineDto, type SparklineDto } from '../../../lib/validation/SparklineDto';
import { isStockDto, type StockDto } from '../../../lib/validation/StockDto';
import { isStockStatDto, type StockStatDto } from '../../../lib/validation/StockStatDto';

export type ListStocksResponse = StockDto[];

export type ListSparklinesResponse = SparklineDto[];

export type ListStockStatsResponse = StockStatDto[];

export function parseListStocks(value: unknown): ListStocksResponse {
  if (!Array.isArray(value) || !value.every(isStockDto)) {
    throw new Error('ListStocks response has an invalid shape');
  }

  return value;
}

export function parseListSparklines(value: unknown): ListSparklinesResponse {
  if (!Array.isArray(value) || !value.every(isSparklineDto)) {
    throw new Error('Sparklines response has an invalid shape');
  }

  return value;
}

export function parseListStockStats(value: unknown): ListStockStatsResponse {
  if (!Array.isArray(value) || !value.every(isStockStatDto)) {
    throw new Error('Sparklines response has an invalid shape');
  }

  return value;
}
