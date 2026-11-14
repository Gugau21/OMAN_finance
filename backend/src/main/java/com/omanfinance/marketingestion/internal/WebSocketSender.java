package com.omanfinance.marketingestion.internal;

/**
 * Sends to the server on an open WebSocket. A {@link WebSocketOpener} hands one to its {@link
 * WebSocketListener} once the WebSocket is open.
 */
interface WebSocketSender {

  /** Sends one text message. Messages are sent in the order of the calls. */
  void send(String message);

  /** Starts a normal close, after the messages already sent. */
  void close();
}
