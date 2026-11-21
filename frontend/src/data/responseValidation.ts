import type { MarketStatus } from '../types/market';
import type { Candle, PricePoint, Sparkline, Stock, StockCandles, StockStat } from '../types/stock';

const rfc3339DateTime = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/;

export function parseMarketStatus(value: unknown): MarketStatus {
  if (
    !isRecord(value) ||
    !isDateTime(value.timestamp) ||
    typeof value.isOpen !== 'boolean' ||
    !isDateTime(value.nextOpen) ||
    !isDateTime(value.nextClose)
  ) {
    throw new Error('Market status response has an invalid shape');
  }

  return {
    timestamp: value.timestamp,
    isOpen: value.isOpen,
    nextOpen: value.nextOpen,
    nextClose: value.nextClose,
  };
}

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
    isDateTime(value.timestamp) &&
    isFiniteNumber(value.price) &&
    isFiniteNumber(value.change) &&
    isFiniteNumber(value.changePercent) &&
    isNonNegativeInteger(value.volume)
  );
}

function isPricePoint(value: unknown): value is PricePoint {
  return isRecord(value) && isDateTime(value.timestamp) && isFiniteNumber(value.price);
}

function isCandle(value: unknown): value is Candle {
  return (
    isRecord(value) &&
    isDateTime(value.timestamp) &&
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isDateTime(value: unknown): value is string {
  return (
    typeof value === 'string' && rfc3339DateTime.test(value) && !Number.isNaN(Date.parse(value))
  );
}
