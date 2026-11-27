import {
  getConfiguredDataSource,
  type DataSource,
  type DataOptions,
} from '../../../data/dataSource';
import { getJson } from '../../../lib/http/client';
import { parseGetMarketStatus, type GetMarketStatusResponse } from './marketStatusDto';
import { getMockMarketStatus } from '../mocks/marketStatusMock';

export async function getMarketStatus(
  sourceOrOptions: DataSource | DataOptions = {},
): Promise<GetMarketStatusResponse | null> {
  const options =
    typeof sourceOrOptions === 'string' ? { source: sourceOrOptions } : sourceOrOptions;
  const source = options.source ?? getConfiguredDataSource('marketStatus');

  if (source === 'mock') {
    return getMockMarketStatus(options.scenario, options.signal);
  }

  const value = await getJson('/api/market/status', {
    signal: options.signal,
    resourceName: 'market status',
  });
  return parseGetMarketStatus(value);
}
