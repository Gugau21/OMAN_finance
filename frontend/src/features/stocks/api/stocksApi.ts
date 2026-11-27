import {
  getConfiguredDataSource,
  type DataSource,
  type DataOptions,
} from '../../../data/dataSource';
import { getJson } from '../../../lib/http/client';
import {
  parseListSparklines,
  parseListStockStats,
  parseListStocks,
  type ListSparklinesResponse,
  type ListStockStatsResponse,
  type ListStocksResponse,
} from './StockApiDto';
import { getMockSparklines, getMockStockStats, getMockStocks } from '../mocks/stockMocks';

export async function getStocks(
  sourceOrOptions: DataSource | DataOptions = {},
): Promise<ListStocksResponse | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('stocks');

  if (source === 'mock') {
    return getMockStocks(options.scenario, options.signal);
  }

  const value = await getJson('/api/stocks', {
    signal: options.signal,
    resourceName: 'stocks',
  });
  return parseListStocks(value);
}

export async function getSparklines(
  sourceOrOptions: DataSource | DataOptions = {},
): Promise<ListSparklinesResponse | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('sparklines');

  if (source === 'mock') {
    return getMockSparklines(options.scenario, options.signal);
  }

  const value = await getJson('/api/stocks/sparkline', {
    signal: options.signal,
    resourceName: 'sparklines',
  });
  return parseListSparklines(value);
}

export async function getStockStats(
  sourceOrOptions: DataSource | DataOptions = {},
): Promise<ListStockStatsResponse | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('stockStats');

  if (source === 'mock') {
    return getMockStockStats(options.scenario, options.signal);
  }

  const value = await getJson('/api/stocks/stat', {
    signal: options.signal,
    resourceName: 'sparklines',
  });
  return parseListStockStats(value);
}
