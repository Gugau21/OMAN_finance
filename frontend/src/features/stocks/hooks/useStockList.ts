import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getConfiguredDataSource } from '../../../data/dataSource';
import { getSparklines, getStockStats, getStocks } from '../api/stocksApi';
import { parseSparklinePointMessage, parseStockStatMessage } from '../realtime/stockMessageParser';
import {
  mergeSparklinePointMessage,
  mergeStockStatMessage,
  toStockListItem,
} from '../mappers/stockListMapper';
import type { StockListItem } from '../model/stock';
import {
  onStompConnected,
  parseStompJson,
  stompTopics,
  subscribeToTopic,
} from '../../../lib/realtime/subscriptions';

export const stockListQueryKey = ['stocks', 'list'] as const;

export function useStockList() {
  const queryClient = useQueryClient();
  const latestStatMessages = useRef(new Map<string, ReturnType<typeof parseStockStatMessage>>());
  const latestSparklineMessages = useRef(
    new Map<string, ReturnType<typeof parseSparklinePointMessage>>(),
  );
  const [realtimeError, setRealtimeError] = useState<Error | null>(null);

  const query = useQuery<StockListItem[] | null>({
    queryKey: stockListQueryKey,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    queryFn: async ({ signal }) => {
      const [stocks, stats, sparklines] = await Promise.all([
        getStocks({ signal }),
        getStockStats({ signal }),
        getSparklines({ signal }),
      ]);

      if (!stocks || !stats || !sparklines) {
        return null;
      }

      let stockList = toStockListItem(stocks, stats, sparklines);
      for (const message of latestStatMessages.current.values()) {
        stockList = mergeStockStatMessage(stockList, message) ?? stockList;
      }
      for (const message of latestSparklineMessages.current.values()) {
        stockList = mergeSparklinePointMessage(stockList, message) ?? stockList;
      }

      return stockList;
    },
  });

  const retry = () => {
    setRealtimeError(null);
    return query.refetch();
  };

  useEffect(() => {
    const unsubscribeFunctions: Array<() => void> = [];
    const hasRealtimeSources =
      getConfiguredDataSource('stockStats') === 'real' ||
      getConfiguredDataSource('sparklines') === 'real';

    if (!hasRealtimeSources) {
      return;
    }

    if (getConfiguredDataSource('stockStats') === 'real') {
      unsubscribeFunctions.push(
        subscribeToTopic(stompTopics.stockStats, (message) => {
          try {
            const update = parseStockStatMessage(parseStompJson(message));
            const previous = latestStatMessages.current.get(update.symbol);
            if (!previous || Date.parse(update.timestamp) >= Date.parse(previous.timestamp)) {
              latestStatMessages.current.set(update.symbol, update);
            }
            setRealtimeError(null);
            queryClient.setQueryData<StockListItem[] | null>(stockListQueryKey, (current) =>
              current === undefined ? current : mergeStockStatMessage(current, update),
            );
          } catch (error) {
            setRealtimeError(
              error instanceof Error ? error : new Error('Invalid stock stat update'),
            );
          }
        }),
      );
    }

    if (getConfiguredDataSource('sparklines') === 'real') {
      unsubscribeFunctions.push(
        subscribeToTopic(stompTopics.stockSparklines, (message) => {
          try {
            const update = parseSparklinePointMessage(parseStompJson(message));
            const previous = latestSparklineMessages.current.get(update.symbol);
            if (!previous || Date.parse(update.timestamp) >= Date.parse(previous.timestamp)) {
              latestSparklineMessages.current.set(update.symbol, update);
            }
            setRealtimeError(null);
            queryClient.setQueryData<StockListItem[] | null>(stockListQueryKey, (current) =>
              current === undefined ? current : mergeSparklinePointMessage(current, update),
            );
          } catch (error) {
            setRealtimeError(
              error instanceof Error ? error : new Error('Invalid sparkline update'),
            );
          }
        }),
      );
    }

    const unsubscribeFromConnect = onStompConnected((reconnected) => {
      if (reconnected) {
        void queryClient.invalidateQueries({ queryKey: stockListQueryKey });
      }
    });

    return () => {
      unsubscribeFunctions.forEach((unsubscribe) => unsubscribe());
      unsubscribeFromConnect();
    };
  }, [queryClient]);

  return {
    ...query,
    retry,
    error: query.error ?? realtimeError,
  };
}
