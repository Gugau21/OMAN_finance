export type DataSource = 'mock' | 'real';

export function getConfiguredDataSource(): DataSource {
  return import.meta.env.VITE_DATA_SOURCE === 'real' ? 'real' : 'mock';
}
