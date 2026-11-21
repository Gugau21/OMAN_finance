package com.omanfinance.marketingestion.internal;

import java.net.URI;

/**
 * Opens a text WebSocket to any server. It only moves text in and out and knows nothing about what
 * the text means. {@link WebSocketAdapter} is the real one; tests use a fake, so {@link
 * AlpacaStreamClient} can be tested without a network.
 */
interface WebSocketOpener {

  /**
   * Starts opening {@code url} and returns at once. Everything that happens on the connection,
   * failing to open included, is reported to {@code listener}, one call at a time.
   */
  void open(URI url, WebSocketListener listener);
}
