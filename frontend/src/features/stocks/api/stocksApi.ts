import {
  getConfiguredDataSource,
  type DataSource,
  type DataOptions,
} from '../../../data/dataSource';
import { getJson } from '../../../lib/http/client';
import {
  parseSparklinesDto,
  parseStockStatsDto,
  parseStocksDto,
  type SparklineDto,
  type StockDto,
  type StockStatDto,
} from './StockApiDto';
import { getMockSparklines, getMockStockStats, getMockStocks } from '../mocks/stockMocks';

export async function getStocks(
  sourceOrOptions: DataSource | DataOptions = {},
): Promise<StockDto[] | null> {
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
  return parseStocksDto(value);
}

export async function getSparklines(
  sourceOrOptions: DataSource | DataOptions = {},
): Promise<SparklineDto[] | null> {
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
  return parseSparklinesDto(value);
}

export async function getStockStats(
  sourceOrOptions: DataSource | DataOptions = {},
): Promise<StockStatDto[] | null> {
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
  return parseStockStatsDto(value);
}
