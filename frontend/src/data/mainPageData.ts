import type { MainPageStock } from '../types/stock';
import { createMainPageStocks } from '../mappers/mainPageStockMapper';
import { getSparklines } from './sparklinesData';
import { getStocks } from './stocksData';
import { getStockStats } from './stockStatsData';

export async function getMainPageStocks(signal?: AbortSignal): Promise<MainPageStock[] | null> {
  const [stocks, stats, sparklines] = await Promise.all([
    getStocks({ signal }),
    getStockStats({ signal }),
    getSparklines({ signal }),
  ]);

  if (!stocks || !stats || !sparklines) {
    return null;
  }

  return createMainPageStocks(stocks, stats, sparklines);
}
