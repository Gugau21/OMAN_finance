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

  public List<Candle> getLastCandles(String symbol, int numberOfCandles) {
    List<Candle> candles = redis.lastCandles(symbol, numberOfCandles);
    // in the future this method will also check for gaps and backfill
    // candles before returning them. For now we assume happy path,
    // so this is just a wrapper on redis

    return candles;
  }
}
