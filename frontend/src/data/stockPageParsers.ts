import type { ChartCandle } from '../components/charts/model/chartTypes';
import {
  isFiniteNumber,
  isNonNegativeInteger,
  isRecord,
  isRfc3339DateTime,
  isSymbol,
} from '../lib/validation/guards';
import { isStockStatDto } from '../features/stocks/dto/stockDtoGuards';
import type { components } from '../types/api.generated';

type StockCandlesDto = components['schemas']['StockCandles'];
type CandleDto = components['schemas']['Candle'];

export type StockPageStat = {
  symbol: string;
  timestamp: Date;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
};

export type StockPageCandles = {
  symbol: string;
  candles: ChartCandle[];
};

export function parseStockStat(value: unknown): StockPageStat {
  if (!isStockStatDto(value)) {
    throw new Error('Stock stat response has an invalid shape');
  }

  return {
    symbol: value.symbol,
    timestamp: new Date(value.timestamp),
    price: value.price,
    change: value.change,
    changePercent: value.changePercent,
    volume: value.volume,
  };
}

export function parseStockCandles(value: unknown): StockPageCandles {
  if (!isStockCandlesDto(value)) {
    throw new Error('Stock candles response has an invalid shape');
  }

  return {
    symbol: value.symbol,
    candles: toChartCandles(value.candles),
  };
}

function isStockCandlesDto(value: unknown): value is StockCandlesDto {
  return (
    isRecord(value) &&
    isSymbol(value.symbol) &&
    Array.isArray(value.candles) &&
    value.candles.length === 60 &&
    value.candles.every(isCandleDto)
  );
}

function isCandleDto(value: unknown): value is CandleDto {
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

function toChartCandles(candles: CandleDto[]): ChartCandle[] {
  return candles.map((candle) => ({
    timestamp: new Date(candle.timestamp),
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
    volume: candle.volume,
  }));
}
