package com.omanfinance.candlebuffer.api;

import com.omanfinance.candlebuffer.api.dto.Candle;
import java.util.List;
import reactor.core.publisher.Flux;

/** The only way other modules read the last MAX_CANDLES candles. */
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
}
