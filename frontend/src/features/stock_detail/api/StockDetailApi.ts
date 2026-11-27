import {
  getConfiguredDataSource,
  type DataSource,
  type DataOptions,
} from '../../../data/dataSource';
import { getJson } from '../../../lib/http/client';
import { HttpError } from '../../../lib/http/errors';
import {
  parseGetStockCandles,
  parseGetStockStat,
  type GetStockCandlesResponse,
  type GetStockStatResponse,
} from './StockDetailApiDto';
import { getMockStockCandles, getMockStockStat } from '../mocks/stockDetailMocks';

export async function getStockCandles(
  symbol: string,
  sourceOrOptions: DataSource | DataOptions = {},
): Promise<GetStockCandlesResponse | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('stockCandles');

  if (source === 'mock') {
    return getMockStockCandles(symbol, options.scenario, options.signal);
  }

  try {
    const value = await getJson(`/api/stocks/${encodeURIComponent(symbol)}/candles`, {
      signal: options.signal,
      resourceName: 'stock candles',
    });
    return parseGetStockCandles(value);
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export async function getStockStatResponse(
  symbol: string,
  sourceOrOptions: DataSource | DataOptions = {},
): Promise<GetStockStatResponse | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('stockStat');

  if (source === 'mock') {
    return getMockStockStat(symbol, options.scenario, options.signal);
  }

  try {
    const value = await getJson(`/api/stocks/${encodeURIComponent(symbol)}/stat`, {
      signal: options.signal,
      resourceName: 'stock stat',
    });
    return parseGetStockStat(value);
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
}
