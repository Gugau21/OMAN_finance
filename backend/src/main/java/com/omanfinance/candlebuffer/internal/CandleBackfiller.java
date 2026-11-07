package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class CandleBackfiller {
  private final CandleRedisRepository redis;
  private final MarketIngestion marketIngestion;
  private static final Logger log = LoggerFactory.getLogger(CandleBackfiller.class);

  CandleBackfiller(CandleRedisRepository redis, MarketIngestion marketIngestion) {
    this.redis = redis;
    this.marketIngestion = marketIngestion;
  }

  List<Candle> fetchAndSaveCandles(String symbol, int numberOfCandles) {
    Instant end = Instant.now();
    Instant start = end.minus(5, ChronoUnit.DAYS);

    BarsRequest barsRequest =
        new BarsRequest(
            List.of(symbol),
            BarsRequest.ONE_MINUTE,
            start,
            end,
            numberOfCandles,
            BarsRequest.Sort.DESC,
            null,
            null,
            null,
            null);

    try {
      BarsResponse barsResponse = marketIngestion.getBars(barsRequest);
      List<Candle> candles = new ArrayList<>();
      for (Bar bar : barsResponse.barsFor(symbol)) {
        try {
          candles.add(Candle.fromBar(bar));
        } catch (NullPointerException e) {
          log.warn("Skipping malformed bar for {}: {}", symbol, bar, e);
        }
      }
      candles = candles.reversed();

      redis.saveCandles(symbol, candles);
      return candles;
    } catch (AlpacaUnavailableException e) {
      log.warn("Backfill failed for {}, starting with empty candles", symbol, e);
      return List.of();
    }
  }
}
