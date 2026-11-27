import { createStockPageView } from './stockPageMapper';
import type { StockPageView } from './stockPageMapper';
import { getConfiguredDataSource } from './dataSource';
import { HttpError } from '../lib/http/errors';
import { getJson } from '../lib/http/client';
import { getMockStockCandles, getMockStockStat } from '../features/stock_detail/mocks/stockDetailMocks';
import { parseStockCandles, parseStockStat } from './stockPageParsers';

export async function getStockPageData(
  symbol: string,
  signal?: AbortSignal,
): Promise<StockPageView | null> {
  const [stat, candles] = await Promise.all([
    getStockStat(symbol, signal),
    getStockCandles(symbol, signal),
  ]);

  if (!stat || !candles) {
    return null;
  }

  return createStockPageView(candles, stat);
}

async function getStockStat(symbol: string, signal?: AbortSignal) {
  if (getConfiguredDataSource('stockStat') === 'mock') {
    const value = await getMockStockStat(symbol, undefined, signal);
    return value ? parseStockStat(value) : null;
  }

  try {
    const value = await getJson(`/api/stocks/${encodeURIComponent(symbol)}/stat`, {
      signal,
      resourceName: 'stock stat',
    });
    return parseStockStat(value);
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

async function getStockCandles(symbol: string, signal?: AbortSignal) {
  if (getConfiguredDataSource('stockCandles') === 'mock') {
    const value = await getMockStockCandles(symbol, undefined, signal);
    return value ? parseStockCandles(value) : null;
  }

  try {
    const value = await getJson(`/api/stocks/${encodeURIComponent(symbol)}/candles`, {
      signal,
      resourceName: 'stock candles',
    });
    return parseStockCandles(value);
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
}
