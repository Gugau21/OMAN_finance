import { isStockStatDto, type StockStatDto } from '../../../lib/validation/StockStatDto';
import { isCandleDto, type CandleDto } from '../../../lib/validation/StockCandlesDto';
import { isSymbol, isRecord } from '../../../lib/validation/guards';

export type StockStatMessage = StockStatDto;
export type StockCandleMessage = CandleDto & { symbol: string };

export function parseStockStatMessage(value: unknown): StockStatMessage {
  if (!isStockStatDto(value)) {
    throw new Error('Sparkline point message has an invalid shape');
  }

  return value;
}

export function parseStockCandleMessage(value: unknown): StockCandleMessage {
  if (!isStockCandleMessage(value)) {
    throw new Error('Stock stat message has an invalid shape');
  }

  return value;
}

function isStockCandleMessage(value: unknown): value is StockCandleMessage {
  return isRecord(value) && isSymbol(value.symbol) && isCandleDto(value);
}
