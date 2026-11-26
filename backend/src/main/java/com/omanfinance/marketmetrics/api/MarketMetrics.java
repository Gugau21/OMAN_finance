package com.omanfinance.marketmetrics.api;

import com.omanfinance.marketmetrics.api.dto.PricePoint;
import com.omanfinance.marketmetrics.api.dto.StockStat;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * The only way other modules read stock stats and sparkline price points. Marketfeed calls this
 * interface and does not call classes/methods within marketmetrics/internal.
 */
public interface MarketMetrics {

  /**
   * A new stock stat each time a candle is saved for a tracked symbol, computed over the last 60
   * stored candles. The stream is shared: every subscriber gets each stat emitted after it
   * subscribes, and nothing is replayed.
   */
  Flux<StockStat> streamStockStats();

  /**
   * Price, change, change percent and volume of {@code symbol} over up to 60 most recent stored
   * candles. When fewer than 60 candles are stored, the window is whatever is present.
   *
   * @return the stat, or {@code null} when nothing is stored for {@code symbol}
   */
  StockStat getStockStat(String symbol);

  /**
   * A new price point (symbol, timestamp and close) each time a candle is saved for a tracked
   * symbol. The stream is shared: every subscriber gets each point emitted after it subscribes, and
   * nothing is replayed.
   */
  Flux<PricePoint> streamPricePoints();

  /**
   * Close price at the start of each of up to 60 most recent stored minutes for {@code symbol},
   * oldest first.
   *
   * @return the points, or {@code null} when nothing is stored for {@code symbol}
   */
  List<PricePoint> getPricePoints(String symbol);
}
