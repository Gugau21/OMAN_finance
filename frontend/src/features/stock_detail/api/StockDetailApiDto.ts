import { isStockCandlesDto, type StockCandlesDto } from '../../../lib/validation/StockCandlesDto';
import { isStockStatDto, type StockStatDto } from '../../../lib/validation/StockStatDto';

export type GetStockCandlesResponse = StockCandlesDto;

export type GetStockStatResponse = StockStatDto;

export function parseGetStockCandles(value: unknown): GetStockCandlesResponse {
  if (!isStockCandlesDto(value)) {
    throw new Error('GetStockCandles response has an invalid shape');
  }

  return value;
}

export function parseGetStockStat(value: unknown): GetStockStatResponse {
  if (!isStockStatDto(value)) {
    throw new Error('GetStockStat response has an invalid shape');
  }

  return value;
}
