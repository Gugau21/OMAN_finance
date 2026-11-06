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
 * The real {@link MarketIngestion}, backed by Alpaca and the {@code Stock} table. Active under
 * every profile except {@code fake}.
 *
 * <p>Skeleton: each method throws {@link UnsupportedOperationException} until the issue that
 * implements it lands. All of them ship together in PR 2 (A2–A5), so this class never reaches
 * {@code main} half-done.
 */
@Component
@Profile("!fake")
public class AlpacaMarketIngestion implements MarketIngestion {

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

  /** Not implemented yet: comes with A2 (loaded once from the {@code Stock} table). */
  @Override
  public List<Stock> getTrackedStocks() {
    throw notYet("getTrackedStocks", "A2");
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
