package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.dto.Candle;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CandleHistory {

  private CandleRedisRepository redis;

  public CandleHistory(CandleRedisRepository redis) {
    this.redis = redis;
  }

  /**
   * Stored candles for {@code symbol}: up to {@code numberOfCandles} of the most recent minutes,
   * oldest first, newest last (same order as the REST contract). Iteration 1 reads Redis only; gap
   * detection and backfill on read come later.
   */
  public List<Candle> getLastCandles(String symbol, int numberOfCandles) {
    List<Candle> candles = redis.getLastCandles(symbol, numberOfCandles);

    return candles;
  }
}
