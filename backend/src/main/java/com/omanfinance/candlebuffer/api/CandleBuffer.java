package com.omanfinance.candlebuffer.api;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * The only way other modules read candles and stock stats. Marketfeed calls this interface and does
 * not call classes/methods within candlebuffer/internal.
 */
public interface CandleBuffer {

  /** {@code true} after startup backfill has finished for every tracked symbol. */
  boolean isReady();

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
   * Starts emitting on {@link #streamStockStat()}: on each saved candle, recomputes a stat from the
   * last {@code windowSize} stored candles for that symbol. Call once at startup from the module
   * that owns the REST/STOMP contract (marketfeed). Must run before anyone subscribes to {@link
   * #streamStockStat()}.
   */
  void ensureStreamStockStatStarted(int windowSize);

  /**
   * A new stock stat each time a candle is saved for a tracked symbol, computed over the window
   * given to {@link #ensureStreamStockStatStarted(int)}. The stream is shared: every subscriber
   * gets each stat emitted after it subscribes, and nothing is replayed.
   *
   * <p><b>Call {@link #ensureStreamStockStatStarted(int)} once before subscribing</b>, or this flux
   * never receives live stats.
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
