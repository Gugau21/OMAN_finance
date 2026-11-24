import {
  Client,
  type IMessage,
  type StompSubscription,
} from '@stomp/stompjs';

export type StompMessageHandler = (message: IMessage) => void;

type SubscriptionEntry = {
  destination: string;
  handler: StompMessageHandler;
  subscription?: StompSubscription;
};

function createWebSocket(): WebSocket {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return new WebSocket(`${protocol}//${window.location.host}/ws`);
}

export class StompConnection {
  private readonly client: Client;
  private readonly entries = new Set<SubscriptionEntry>();

  constructor() {
    this.client = new Client({
      webSocketFactory: createWebSocket,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      reconnectDelay: 10_000,
      onConnect: () => this.subscribeAll(),
    });
  }

  subscribe(destination: string, handler: StompMessageHandler): () => void {
    const entry: SubscriptionEntry = { destination, handler };
    this.entries.add(entry);

    if (!this.client.active) {
      this.client.activate();
    } else if (this.client.connected) {
      entry.subscription = this.client.subscribe(destination, handler);
    }

    return () => {
      entry.subscription?.unsubscribe();
      this.entries.delete(entry);

      if (this.entries.size === 0) {
        void this.client.deactivate();
      }
    };
  }

  private subscribeAll(): void {
    for (const entry of this.entries) {
      entry.subscription?.unsubscribe();
      entry.subscription = this.client.subscribe(entry.destination, entry.handler);
    }
  }
}

export const stompConnection = new StompConnection();
