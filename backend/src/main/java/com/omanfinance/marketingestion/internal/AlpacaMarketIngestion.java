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
 * <p>Skeleton: each method throws {@link UnsupportedOperationException} until the issue that
 * implements it lands. All of them ship together in PR 2 (A2–A5), so this class never reaches
 * {@code main} half-done.
 */
@Component
@Profile("!fake")
public class AlpacaMarketIngestion implements MarketIngestion {

  private final List<Stock> trackedStocks;

  /**
   * Reads the tracked stocks from the {@code stock} table once, so {@link #getTrackedStocks} never
   * queries the database.
   *
   * @throws IllegalStateException when the table is empty, so the application does not start
   */
  AlpacaMarketIngestion(StockRepository stocks) {
    this.trackedStocks = stocks.findAllByOrderByIdAsc().stream().map(StockEntity::toStock).toList();
    if (trackedStocks.isEmpty()) {
      throw new IllegalStateException("The stock table is empty: no stocks to track");
    }
  }

  /** Not implemented yet: comes with A7 (live bars from the Alpaca WebSocket). */
  @Override
  public Flux<Bar> streamBars() {
    throw notYet("streamBars", "A7");
  }

  /** Not implemented yet: comes with A3 (Alpaca REST client and Redis bar cache). */
  @Override
  public BarsResponse getBars(BarsRequest request) {
    throw notYet("getBars", "A3");
  }

  /** Not implemented yet: comes with A4 ({@code MarketClock}). */
  @Override
  public Clock getMarketStatus() {
    throw notYet("getMarketStatus", "A4");
  }

  /** Not implemented yet: comes with A4 ({@code MarketClock}). */
  @Override
  public Flux<Clock> streamMarketStatus() {
    throw notYet("streamMarketStatus", "A4");
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
