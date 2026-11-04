package com.omanfinance.marketingestion;

import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Flux;

/**
 * The only way other modules get market data. Bars and market status are passed on exactly as
 * Alpaca sends them.
 */
public interface MarketIngestion {

  /**
   * Live minute bars for every tracked symbol, as they arrive from the Alpaca stream. The stream is
   * shared: every subscriber gets each bar that arrives after it subscribes, and nothing is
   * replayed. It does not complete while the application runs; a dropped Alpaca connection is
   * reconnected without ending the stream.
   */
  Flux<Bar> streamBars();

  /**
   * Historical bars from Alpaca {@code GET /v2/stocks/bars}, or from the raw bar cache when it
   * already holds the whole requested range. Follows {@code next_page_token} until Alpaca has no
   * more pages and returns them all in one response, so its {@code nextPageToken} is always null.
   * Blocks until the bars are available.
   *
   * @throws RuntimeException when Alpaca cannot be reached or rejects the request
   */
  BarsResponse getBars(BarsRequest request);

  /** The last market status Alpaca returned, or empty before the first response has arrived. */
  Optional<Clock> getMarketStatus();

  /**
   * Every market status Alpaca returns: one item per {@code /v2/clock} response, at startup, when
   * the market opens or closes and on the periodic refresh, even when nothing but the {@code
   * timestamp} changed. A new subscriber first gets the current status, if there is one. It does
   * not complete while the application runs.
   */
  Flux<Clock> streamMarketStatus();

  /** The symbols currently tracked, in a stable order. The list is unmodifiable. */
  List<String> getTrackedSymbols();

  /**
   * Starts tracking {@code symbol}: stores it and subscribes the live stream to it. Not implemented
   * in this iteration.
   *
   * @return {@code false} if the symbol was already tracked
   * @throws UnsupportedOperationException always, in this iteration
   */
  boolean addSymbol(String symbol);

  /**
   * Stops tracking {@code symbol}: removes it and unsubscribes the live stream from it. Not
   * implemented in this iteration.
   *
   * @return {@code false} if the symbol was not tracked
   * @throws UnsupportedOperationException always, in this iteration
   */
  boolean removeSymbol(String symbol);
}
