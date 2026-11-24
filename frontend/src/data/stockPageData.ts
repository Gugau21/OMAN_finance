import { createStockPageView } from '../mappers/stockPageMapper';
import type { StockPageView } from '../features/stocks/model/stock';
import { getStockCandles, getStockStat } from '../features/stocks/api/stocksApi';

export async function getStockPageData(
  symbol: string,
  signal?: AbortSignal,
): Promise<StockPageView | null> {
  const [stat, candles] = await Promise.all([
    getStockStat(symbol, { signal }),
    getStockCandles(symbol, { signal }),
  ]);

  if (!stat || !candles) {
    return null;
  }

  return createStockPageView(candles, stat);
}
