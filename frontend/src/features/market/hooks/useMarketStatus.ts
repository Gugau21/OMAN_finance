import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getConfiguredDataSource } from '../../../data/dataSource';
import { getMarketStatus } from '../api/marketApi';
import { parseGetMarketStatus } from '../api/marketStatusDto';
import { toMarketStatus } from '../mappers/marketMapper';
import type { MarketStatus } from '../model/market';
import {
  parseStompJson,
  onStompConnected,
  stompTopics,
  subscribeToTopic,
} from '../../../lib/realtime/subscriptions';

export const marketStatusQueryKey = ['market', 'status'] as const;

export function useMarketStatus() {
  const queryClient = useQueryClient();
  const latestMessageTimestamp = useRef<Date | null>(null);
  const [realtimeError, setRealtimeError] = useState<Error | null>(null);

  const query = useQuery<MarketStatus | null>({
    queryKey: marketStatusQueryKey,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    queryFn: async ({ signal }) => {
      const snapshotDto = await getMarketStatus({ signal });
      const snapshot = snapshotDto ? toMarketStatus(snapshotDto) : null;
      const latestTimestamp = latestMessageTimestamp.current;

      if (snapshot && latestTimestamp && snapshot.timestamp.getTime() < latestTimestamp.getTime()) {
        return queryClient.getQueryData<MarketStatus | null>(marketStatusQueryKey) ?? snapshot;
      }

      return snapshot;
    },
  });

  useEffect(() => {
    if (getConfiguredDataSource('marketStatus') !== 'real') {
      return;
    }

    const unsubscribe = subscribeToTopic(stompTopics.marketStatus, (message) => {
      try {
        const updateDto = parseGetMarketStatus(parseStompJson(message));
        const update = toMarketStatus(updateDto);
        latestMessageTimestamp.current = update.timestamp;
        setRealtimeError(null);
        queryClient.setQueryData<MarketStatus | null>(marketStatusQueryKey, (current) => {
          if (current && update.timestamp.getTime() < current.timestamp.getTime()) {
            return current;
          }

          return update;
        });
      } catch (error) {
        setRealtimeError(
          error instanceof Error ? error : new Error('Invalid market status update'),
        );
      }
    });

    const unsubscribeFromConnect = onStompConnected((reconnected) => {
      if (reconnected) {
        void queryClient.invalidateQueries({ queryKey: marketStatusQueryKey });
      }
    });

    return () => {
      unsubscribe();
      unsubscribeFromConnect();
    };
  }, [queryClient]);

  return {
    ...query,
    error: query.error ?? realtimeError,
  };
}
