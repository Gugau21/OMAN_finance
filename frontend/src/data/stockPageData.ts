import { createStockPageView } from '../mappers/stockPageMapper';
import type { StockPageView } from '../types/stock';
import { getStockCandles } from './stockCandlesData';
import { getStockStat } from './stockStatData';

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
