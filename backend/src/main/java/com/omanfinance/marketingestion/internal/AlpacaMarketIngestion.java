package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import com.omanfinance.marketingestion.api.dto.Clock;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * The real {@link MarketIngestion}, backed by Alpaca and the {@code stock} table. Active under
 * every profile except {@code fake}.
 *
 * <p>The two streams are not implemented yet and throw {@link UnsupportedOperationException}: they
 * ship in PR 3 (A6, A7). Until then, other modules use the {@code fake} profile to read them.
 */
@Component
@Profile("!fake")
public class AlpacaMarketIngestion implements MarketIngestion {

  private final List<Stock> trackedStocks;
  private final CachingBarsClient barsClient;
  private final MarketClock marketClock;

  /**
   * Reads the tracked stocks from the {@code stock} table once, so {@link #getTrackedStocks} never
   * queries the database.
   *
   * @throws IllegalStateException when the table is empty, so the application does not start
   */
  AlpacaMarketIngestion(
      StockRepository stocks, CachingBarsClient barsClient, MarketClock marketClock) {
    this.trackedStocks = stocks.findAllByOrderByIdAsc().stream().map(StockEntity::toStock).toList();
    if (trackedStocks.isEmpty()) {
      throw new IllegalStateException("The stock table is empty: no stocks to track");
    }
    this.barsClient = barsClient;
    this.marketClock = marketClock;
  }

  /** Not implemented yet: comes with A7 (live bars from the Alpaca WebSocket). */
  @Override
  public Flux<Bar> streamBars() {
    throw notYet("streamBars", "A7");
  }

  /**
   * Every page of bars from Alpaca {@code GET /v2/stocks/bars}, or the same bars from the Redis bar
   * cache when it holds every requested minute (see {@link CachingBarsClient}).
   */
  @Override
  public BarsResponse getBars(BarsRequest request) {
    return barsClient.getBars(request);
  }

  /** The status {@link MarketClock} holds; it was fetched before this bean could be created. */
  @Override
  public Clock getMarketStatus() {
    return marketClock.current();
  }

  /** Not implemented yet: comes with A6 (market status pushed to browsers). */
  @Override
  public Flux<Clock> streamMarketStatus() {
    throw notYet("streamMarketStatus", "A6");
  }

  /** The stocks read from the {@code stock} table at startup, ordered by {@code id}. */
  @Override
  public List<Stock> getTrackedStocks() {
    return trackedStocks;
  }

  @Override
  public boolean addSymbol(String symbol) {
    throw new UnsupportedOperationException("addSymbol is not implemented in this iteration");
  }

  @Override
  public boolean removeSymbol(String symbol) {
    throw new UnsupportedOperationException("removeSymbol is not implemented in this iteration");
  }

  private static UnsupportedOperationException notYet(String method, String issue) {
    return new UnsupportedOperationException(method + " is not implemented yet (" + issue + ")");
  }
}
