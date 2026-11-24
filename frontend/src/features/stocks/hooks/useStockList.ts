import { useQuery } from '@tanstack/react-query';
import { getStockList } from '../api/stocksApi';

export const stockListQueryKey = ['stocks', 'list'] as const;

export function useStockList() {
  return useQuery({
    queryKey: stockListQueryKey,
    queryFn: ({ signal }) => getStockList(signal),
  });
}
