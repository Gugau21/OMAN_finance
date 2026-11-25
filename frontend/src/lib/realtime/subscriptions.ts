import type { IMessage } from '@stomp/stompjs';
import {
  stompConnection,
  type StompConnectionHandler,
  type StompMessageHandler,
} from './stompClient';

export const stompTopics = {
  marketStatus: '/topic/market/status',
  stockStats: '/topic/stocks/stat',
  stockSparklines: '/topic/stocks/sparkline',
  stockStat: (symbol: string) => `/topic/stocks/${encodeURIComponent(symbol)}/stat`,
  stockCandles: (symbol: string) => `/topic/stocks/${encodeURIComponent(symbol)}/candles`,
} as const;

export function subscribeToTopic(destination: string, handler: StompMessageHandler): () => void {
  return stompConnection.subscribe(destination, handler);
}

export function onStompConnected(handler: StompConnectionHandler): () => void {
  return stompConnection.onConnected(handler);
}

export function parseStompJson(message: IMessage): unknown {
  return JSON.parse(message.body) as unknown;
}
