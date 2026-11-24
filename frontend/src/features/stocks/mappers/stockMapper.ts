import type { Sparkline, Stock, StockCandles, StockStat } from '../model/stock';
import {
  isChartCandle,
  isChartPricePoint,
  parseChartCandles,
  parseChartPricePoints,
} from '../../../components/charts/mappers/chartPayloadMapper';
import {
  isFiniteNumber,
  isNonNegativeInteger,
  isRecord,
  isRfc3339DateTime,
} from '../../../lib/validation/guards';

export function parseStocks(value: unknown): Stock[] {
  if (!Array.isArray(value) || !value.every(isStock)) {
    throw new Error('Stocks response has an invalid shape');
  }

  return value;
}

export function parseSparklines(value: unknown): Sparkline[] {
  if (!Array.isArray(value) || !value.every(isSparkline)) {
    throw new Error('Sparklines response has an invalid shape');
  }

  return value.map((sparkline) => ({
    symbol: sparkline.symbol,
    points: parseChartPricePoints(sparkline.points).map((point) => ({
      timestamp: point.timestamp,
      price: point.price,
      ...(point.volume === undefined ? {} : { volume: point.volume }),
    })),
  }));
}

export function parseStockStats(value: unknown): StockStat[] {
  if (!Array.isArray(value) || !value.every(isStockStat)) {
    throw new Error('Stock stats response has an invalid shape');
  }

  return value.map((stat) => ({
    ...stat,
    timestamp: new Date(stat.timestamp),
  }));
}

export function parseStockStat(value: unknown): StockStat {
  if (!isStockStat(value)) {
    throw new Error('Stock stat response has an invalid shape');
  }

  return {
    ...value,
    timestamp: new Date(value.timestamp),
  };
}

export function parseStockCandles(value: unknown): StockCandles {
  if (!isStockCandles(value)) {
    throw new Error('Stock candles response has an invalid shape');
  }

  return {
    symbol: value.symbol,
    candles: parseChartCandles(value.candles).map((candle) => ({
      timestamp: candle.timestamp,
      open: candle.open,
      high: candle.high,
      low: candle.low,
      close: candle.close,
      volume: candle.volume,
    })),
  };
}

type StockStatPayload = {
  symbol: string;
  timestamp: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
};

type StockCandlesPayload = {
  symbol: string;
  candles: unknown[];
};

type SparklinePayload = {
  symbol: string;
  points: unknown[];
};

function isStockCandles(value: unknown): value is StockCandlesPayload {
  return (
    isRecord(value) &&
    isNonEmptyString(value.symbol) &&
    Array.isArray(value.candles) &&
    value.candles.length === 60 &&
    value.candles.every(isCandle)
  );
}

function isStock(value: unknown): value is Stock {
  return isRecord(value) && isNonEmptyString(value.symbol) && isNonEmptyString(value.name);
}

function isSparkline(value: unknown): value is SparklinePayload {
  return (
    isRecord(value) &&
    isNonEmptyString(value.symbol) &&
    Array.isArray(value.points) &&
    value.points.length === 60 &&
    value.points.every(isPricePoint)
  );
}

function isStockStat(value: unknown): value is StockStatPayload {
  return (
    isRecord(value) &&
    isNonEmptyString(value.symbol) &&
    isRfc3339DateTime(value.timestamp) &&
    isFiniteNumber(value.price) &&
    isFiniteNumber(value.change) &&
    isFiniteNumber(value.changePercent) &&
    isNonNegativeInteger(value.volume)
  );
}

function isPricePoint(value: unknown): boolean {
  return isChartPricePoint(value);
}

function isCandle(value: unknown): boolean {
  return isChartCandle(value);
}


function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
