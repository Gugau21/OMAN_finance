import type {
  StockCandles,
  StockPageView,
  StockStat,
} from '../features/stocks/model/stock';
import type { ChartPricePoint } from '../components/charts/model/chartTypes';

export function createStockPageView(candles: StockCandles, stat: StockStat): StockPageView {
  if (candles.symbol !== stat.symbol) {
    throw new Error(`Cannot create stock page view for ${candles.symbol} and ${stat.symbol}`);
  }

  const sparkline: ChartPricePoint[] = candles.candles.map((candle) => ({
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
