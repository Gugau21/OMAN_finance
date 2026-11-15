package com.omanfinance.marketingestion.internal;

/**
 * Is told what happens on one WebSocket opened by a {@link WebSocketOpener}, one call at a time and
 * in this order: opened, messages received, closed or failed.
 */
interface WebSocketListener {

  /** The WebSocket is open; {@code sender} sends on it. */
  void onOpened(WebSocketSender sender);

  /**
   * The server sent a text message. It is always the whole message, even when it arrived in several
   * pieces.
   */
  void onMessageReceived(String message);

  /** The server closed the connection. */
  void onClosed(int statusCode, String reason);

  /** The connection could not be opened or broke. Nothing more is reported after this. */
  void onFailed(Throwable error);
}
