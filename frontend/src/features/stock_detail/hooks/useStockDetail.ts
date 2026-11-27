import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getConfiguredDataSource } from '../../../data/dataSource';
import { getStockCandles, getStockStatResponse } from '../api/StockDetailApi';
import {
  parseStockCandleMessage,
  parseStockStatMessage,
} from '../realtime/stockDetailMessageParser';
import {
  mergeStockCandleMessage,
  mergeStockStatMessage,
  toStockDetail,
} from '../mappers/stockDetailMapper';
import type { StockDetail } from '../model/stock_detail';
import {
  onStompConnected,
  parseStompJson,
  stompTopics,
  subscribeToTopic,
} from '../../../lib/realtime/subscriptions';

export const stockDetailQueryKey = (symbol: string) => ['stocks', 'detail', symbol] as const;

export function useStockDetail(symbol: string) {
  const queryClient = useQueryClient();
  const queryKey = stockDetailQueryKey(symbol);
  const latestStatMessage = useRef<{
    symbol: string;
    data: ReturnType<typeof parseStockStatMessage>;
  } | null>(null);
  const latestCandleMessage = useRef<{
    symbol: string;
    data: ReturnType<typeof parseStockCandleMessage>;
  } | null>(null);
  const [prevSymbol, setPrevSymbol] = useState(symbol);
  const [realtimeError, setRealtimeError] = useState<Error | null>(null);

  if (prevSymbol !== symbol) {
    setPrevSymbol(symbol);
    setRealtimeError(null);
  }

  const query = useQuery<StockDetail | null>({
    queryKey,
    enabled: Boolean(symbol),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    queryFn: async ({ signal }) => {
      const [statResponse, candlesResponse] = await Promise.all([
        getStockStatResponse(symbol, { signal }),
        getStockCandles(symbol, { signal }),
      ]);

      if (!statResponse || !candlesResponse) {
        return null;
      }

      let detail = toStockDetail(statResponse, candlesResponse);
      const statEntry = latestStatMessage.current;
      if (statEntry && statEntry.symbol === symbol) {
        detail = mergeStockStatMessage(detail, statEntry.data) ?? detail;
      }
      const candleEntry = latestCandleMessage.current;
      if (candleEntry && candleEntry.symbol === symbol) {
        detail = mergeStockCandleMessage(detail, candleEntry.data) ?? detail;
      }

      return detail;
    },
  });

  const retry = () => {
    setRealtimeError(null);
    return query.refetch();
  };

  useEffect(() => {
    if (!symbol) {
      return;
    }

    const unsubscribeFunctions: Array<() => void> = [];
    const hasRealtimeSources =
      getConfiguredDataSource('stockStat') === 'real' ||
      getConfiguredDataSource('stockCandles') === 'real';

    if (!hasRealtimeSources) {
      return;
    }

    if (getConfiguredDataSource('stockStat') === 'real') {
      unsubscribeFunctions.push(
        subscribeToTopic(stompTopics.stockStat(symbol), (message) => {
          try {
            const update = parseStockStatMessage(parseStompJson(message));
            if (update.symbol !== symbol) {
              return;
            }
            const previous = latestStatMessage.current;
            const previousData = previous?.symbol === symbol ? previous.data : null;

            if (
              !previousData ||
              Date.parse(update.timestamp) >= Date.parse(previousData.timestamp)
            ) {
              latestStatMessage.current = { symbol, data: update };
            }
            setRealtimeError(null);
            queryClient.setQueryData<StockDetail | null>(queryKey, (current) =>
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

    if (getConfiguredDataSource('stockCandles') === 'real') {
      unsubscribeFunctions.push(
        subscribeToTopic(stompTopics.stockCandles(symbol), (message) => {
          try {
            const update = parseStockCandleMessage(parseStompJson(message));
            if (update.symbol !== symbol) {
              return;
            }
            const previous = latestCandleMessage.current;
            const previousData = previous?.symbol === symbol ? previous.data : null;

            if (
              !previousData ||
              Date.parse(update.timestamp) >= Date.parse(previousData.timestamp)
            ) {
              latestCandleMessage.current = { symbol, data: update };
            }
            setRealtimeError(null);
            queryClient.setQueryData<StockDetail | null>(queryKey, (current) =>
              current === undefined ? current : mergeStockCandleMessage(current, update),
            );
          } catch (error) {
            setRealtimeError(
              error instanceof Error ? error : new Error('Invalid stock candle update'),
            );
          }
        }),
      );
    }

    const unsubscribeFromConnect = onStompConnected((reconnected) => {
      if (reconnected) {
        void queryClient.invalidateQueries({ queryKey });
      }
    });

    return () => {
      unsubscribeFunctions.forEach((unsubscribe) => unsubscribe());
      unsubscribeFromConnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryClient, symbol]);

  return {
    ...query,
    retry,
    error: query.error ?? realtimeError,
  };
}
