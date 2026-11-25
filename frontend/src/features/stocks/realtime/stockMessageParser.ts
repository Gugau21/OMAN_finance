import { isRecord, isSymbol } from '../../../lib/validation/guards';
import type { components } from '../../../types/api.generated';
import { isPricePointDto, isStockStatDto } from '../dto/stockDtoGuards';

export type SparklinePointMessageDto = components['schemas']['PricePoint'] &
  Pick<components['schemas']['Stock'], 'symbol'>;
export type StockStatMessageDto = components['schemas']['StockStat'];

export function parseSparklinePointMessage(value: unknown): SparklinePointMessageDto {
  if (!isSparklinePointMessageDto(value)) {
    throw new Error('Sparkline point message has an invalid shape');
  }

  return value;
}

export function parseStockStatMessage(value: unknown): StockStatMessageDto {
  if (!isStockStatDto(value)) {
    throw new Error('Stock stat message has an invalid shape');
  }

  return value;
}

function isSparklinePointMessageDto(value: unknown): value is SparklinePointMessageDto {
  return isRecord(value) && isSymbol(value.symbol) && isPricePointDto(value);
}
