package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import com.omanfinance.marketingestion.api.dto.Clock;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * The real {@link MarketIngestion}, backed by Alpaca and the {@code stock} table. Active under
 * every profile except {@code fake}.
 *
 * <p>Live bars come from the Alpaca stream, which {@link AlpacaStreamClient} opens once the
 * application is ready. The market status comes from {@link MarketStatusTracker}.
 */
@Component
@Profile("!fake")
public class AlpacaMarketIngestion implements MarketIngestion {

  private final List<Stock> trackedStocks;
  private final BarHistory barHistory;
  private final MarketStatusTracker statusTracker;
  private final LiveBarStream liveBars;
  private final AlpacaStreamClient streamClient;

  /**
   * Reads the tracked stocks from the {@code stock} table once, so {@link #getTrackedStocks} never
   * queries the database.
   *
   * @throws IllegalStateException when the table is empty, so the application does not start
   */
  AlpacaMarketIngestion(
      StockRepository stocks,
      BarHistory barHistory,
      MarketStatusTracker statusTracker,
      LiveBarStream liveBars,
      AlpacaStreamClient streamClient) {
    this.trackedStocks = stocks.findAllByOrderByIdAsc().stream().map(StockEntity::toStock).toList();
    if (trackedStocks.isEmpty()) {
      throw new IllegalStateException("The stock table is empty: no stocks to track");
    }
    this.barHistory = barHistory;
    this.statusTracker = statusTracker;
    this.liveBars = liveBars;
    this.streamClient = streamClient;
  }

  /**
   * Connects to the Alpaca stream for the tracked symbols once the application is ready, so the
   * connection is not opened while beans are still being created. Returns at once; the stream
   * reconnects by itself when it drops.
   */
  @EventListener(ApplicationReadyEvent.class)
  void startLiveBars() {
    streamClient.connect(trackedStocks.stream().map(Stock::symbol).toList());
  }

  /**
   * The live minute bars from the Alpaca stream, shared by every subscriber (see {@link
   * LiveBarStream}). Subscribing does not open a connection: the one stream connection is opened by
   * {@link #startLiveBars} and stays open while the application runs.
   */
  @Override
  public Flux<Bar> streamBars() {
    return liveBars.flux();
  }

  /**
   * Every page of bars from Alpaca {@code GET /v2/stocks/bars}, or the same bars from the Redis bar
   * cache when it holds every requested minute (see {@link BarHistory}).
   */
  @Override
  public BarsResponse getBars(BarsRequest request) {
    return barHistory.getBars(request);
  }

  /**
   * The status {@link MarketStatusTracker} holds; it was fetched before this bean could be created.
   */
  @Override
  public Clock getMarketStatus() {
    return statusTracker.current();
  }

  /**
   * Every status {@link MarketStatusTracker} fetches from Alpaca, starting with the current one for
   * each new subscriber.
   */
  @Override
  public Flux<Clock> streamMarketStatus() {
    return statusTracker.statuses();
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
}
