import {
  isFiniteNumber,
  isNonNegativeInteger,
  isRecord,
  isRfc3339DateTime,
} from '../../../lib/validation/guards';
import type { ChartCandle, ChartPricePoint } from '../model/chartTypes';

export function parseChartPricePoint(value: unknown): ChartPricePoint {
  if (!isChartPricePoint(value)) {
    throw new Error('Price point response has an invalid shape');
  }

  return {
    timestamp: new Date(value.timestamp),
    price: value.price,
    ...(value.volume === undefined ? {} : { volume: value.volume }),
  };
}

export function parseChartPricePoints(value: unknown): ChartPricePoint[] {
  if (!Array.isArray(value) || !value.every(isChartPricePoint)) {
    throw new Error('Price points response has an invalid shape');
  }

  return value.map((point) => ({
    timestamp: new Date(point.timestamp),
    price: point.price,
    ...(point.volume === undefined ? {} : { volume: point.volume }),
  }));
}

export function parseChartCandle(value: unknown): ChartCandle {
  if (!isChartCandle(value)) {
    throw new Error('Candle response has an invalid shape');
  }
  return {
    timestamp: new Date(value.timestamp),
    open: value.open,
    high: value.high,
    low: value.low,
    close: value.close,
    volume: value.volume,
  };
}

export function parseChartCandles(value: unknown): ChartCandle[] {
  if (!Array.isArray(value) || !value.every(isChartCandle)) {
    throw new Error('Candles response has an invalid shape');
  }

  return value.map((candle) => ({
    timestamp: new Date(candle.timestamp),
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
    volume: candle.volume,
  }));
}

export function isChartPricePoint(
  value: unknown,
): value is { timestamp: string; price: number; volume?: number } {
  return (
    isRecord(value) &&
    isRfc3339DateTime(value.timestamp) &&
    isFiniteNumber(value.price) &&
    (value.volume === undefined ||
      isNonNegativeInteger(value.volume))
  );
}

export function isChartCandle(
  value: unknown,
): value is {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
} {
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

