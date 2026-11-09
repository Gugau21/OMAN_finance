package com.omanfinance.candlebuffer.api;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * The only way other modules read candles and stock stats. ClientFeed calls this interface and does
 * not call classes/methods within candlebuffer/internal.
 */
public interface CandleBuffer {

  /**
   * Live candles for every tracked symbol, emitted only after each one has been saved. The stream
   * is shared: every subscriber gets each candle that arrives after it subscribes, and nothing is
   * replayed.
   */
  Flux<Candle> streamCandles();

  /**
   * Stored candles for {@code symbol}: up to {@code numberOfCandles} of the most recent minutes,
   * oldest first, newest last. Fewer than {@code numberOfCandles} when less is stored; empty when
   * none, including an unknown symbol. Iteration 1 reads Redis only; gap detection and backfill on
   * read come later.
   */
  List<Candle> getLastCandles(String symbol, int numberOfCandles);

  /**
   * A new stock stat each time a candle is saved for a tracked symbol, computed from that symbol's
   * last 60 candles. The stream is shared: every subscriber gets each stat emitted after it
   * subscribes, and nothing is replayed.
   */
  Flux<StockStat> streamStockStat();

  /**
   * Close price at the start of each of up to {@code numberOfPricePoints} most recent stored
   * minutes for {@code symbol}, oldest first.
   *
   * @return the points, or {@code null} when nothing is stored for {@code symbol}
   */
  List<PricePoint> getPricePoints(String symbol, int numberOfPricePoints);

  /**
   * Price, change, change percent and volume of {@code symbol} over up to {@code windowSize} most
   * recent stored candles. When fewer than {@code windowSize} candles are stored, the window is
   * whatever is present.
   *
   * <ul>
   *   <li>{@code price}: latest close
   *   <li>{@code change}: {@code price} minus the open of the oldest candle in the window
   *   <li>{@code changePercent}: {@code change / that open × 100}
   *   <li>{@code volume}: sum of volumes in the window
   *   <li>{@code timestamp}: latest open time in the window
   * </ul>
   *
   * @return the stat, or {@code null} when nothing is stored for {@code symbol}
   */
  StockStat getStockStat(String symbol, int windowSize);
}
