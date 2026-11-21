import type { StockCandles, StockPageView, StockPricePoint, StockStat } from '../types/stock';

export function createStockPageView(candles: StockCandles, stat: StockStat): StockPageView {
  if (candles.symbol !== stat.symbol) {
    throw new Error(`Cannot create stock page view for ${candles.symbol} and ${stat.symbol}`);
  }

  const sparkline: StockPricePoint[] = candles.candles.map((candle) => ({
    timestamp: candle.timestamp,
    price: candle.close,
    volume: candle.volume,
  }));

  return {
    candles,
    stat,
    sparkline,
  };
}
