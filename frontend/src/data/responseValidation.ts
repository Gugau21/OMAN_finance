import type { Candle, PricePoint, Sparkline, Stock, StockCandles, StockStat } from '../types/stock';
import { isRecord, isRfc3339DateTime } from '../lib/validation/guards';

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

  return value;
}

export function parseStockStats(value: unknown): StockStat[] {
  if (!Array.isArray(value) || !value.every(isStockStat)) {
    throw new Error('Stock stats response has an invalid shape');
  }

  return value;
}

export function parseStockStat(value: unknown): StockStat {
  if (!isStockStat(value)) {
    throw new Error('Stock stat response has an invalid shape');
  }

  return value;
}

export function parseStockCandles(value: unknown): StockCandles {
  if (!isStockCandles(value)) {
    throw new Error('Stock candles response has an invalid shape');
  }

  return value;
}

function isStockCandles(value: unknown): value is StockCandles {
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

function isSparkline(value: unknown): value is Sparkline {
  return (
    isRecord(value) &&
    isNonEmptyString(value.symbol) &&
    Array.isArray(value.points) &&
    value.points.length === 60 &&
    value.points.every(isPricePoint)
  );
}

function isStockStat(value: unknown): value is StockStat {
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

function isPricePoint(value: unknown): value is PricePoint {
  return   isRecord(value) && isRfc3339DateTime(value.timestamp) && isFiniteNumber(value.price);
}

function isCandle(value: unknown): value is Candle {
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

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

