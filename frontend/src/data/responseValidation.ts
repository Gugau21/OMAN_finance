import type { MarketStatus } from '../types/market';
import type { PricePoint, Sparkline, Stock } from '../types/stock';

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

function isStock(value: unknown): value is Stock {
  return (
    isRecord(value) &&
    isNonEmptyString(value.symbol) &&
    isNonEmptyString(value.name)
  );
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

function isPricePoint(value: unknown): value is PricePoint {
  return (
    isRecord(value) &&
    isDateTime(value.timestamp) &&
    typeof value.price === 'number' &&
    Number.isFinite(value.price)
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isDateTime(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    rfc3339DateTime.test(value) &&
    !Number.isNaN(Date.parse(value))
  );
}
