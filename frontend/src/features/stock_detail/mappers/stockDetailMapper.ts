import type { StockDetail, StockStat } from '../model/stock_detail';
import type { GetStockCandlesResponse, GetStockStatResponse } from '../api/StockDetailApiDto';
import type { StockCandleMessage, StockStatMessage } from '../realtime/stockDetailMessageParser';

export function toStockDetail(
  stat: GetStockStatResponse,
  candles: GetStockCandlesResponse,
): StockDetail {
  if (stat.symbol !== candles.symbol) {
    throw new Error('toStockDetail stats and candles have different symbol');
  }
  return {
    symbol: stat.symbol,
    stat: {
      price: stat.price,
      change: stat.change,
      changePercent: stat.changePercent,
      volume: stat.volume,
      timestamp: new Date(stat.timestamp),
    },
    sparkline: {
      points: candles.candles.map((candle) => ({
        price: candle.close,
        volume: candle.volume,
        timestamp: new Date(candle.timestamp),
      })),
    },
    candles: {
      candles: candles.candles.map((candle) => ({
        ...candle,
        timestamp: new Date(candle.timestamp),
      })),
    },
  };
}

export function mergeStockStatMessage(
  stock: StockDetail | null,
  message: StockStatMessage,
): StockDetail | null {
  if (!stock) {
    return stock;
  }
  if (stock.symbol !== message.symbol) {
    throw new Error('mergeStockStatMessage stock and message have different symbol');
  }
  const timestamp = new Date(message.timestamp);

  if (timestamp.getTime() < stock.stat.timestamp.getTime()) {
    return stock;
  }

  const stat: StockStat = {
    timestamp,
    price: message.price,
    change: message.change,
    changePercent: message.changePercent,
    volume: message.volume,
  };

  return { ...stock, stat };
}

export function mergeStockCandleMessage(
  stock: StockDetail | null,
  message: StockCandleMessage,
): StockDetail | null {
  if (!stock) {
    return stock;
  }
  if (stock.symbol !== message.symbol) {
    throw new Error('mergeStockStatMessage stock and message have different symbol');
  }
  const new_candle = { ...message, timestamp: new Date(message.timestamp) };

  const candles = stock.candles.candles;
  const lastTimestamp = candles[candles.length - 1]?.timestamp.getTime();
  if (lastTimestamp !== undefined && new_candle.timestamp.getTime() < lastTimestamp) {
    return stock;
  }

  const updatedCandles = candles
    .filter((point) => point.timestamp.getTime() !== new_candle.timestamp.getTime())
    .concat(new_candle)
    .sort((left, right) => left.timestamp.getTime() - right.timestamp.getTime())
    .slice(-60);

  return {
    ...stock,
    candles: { candles: updatedCandles },
  };
}
