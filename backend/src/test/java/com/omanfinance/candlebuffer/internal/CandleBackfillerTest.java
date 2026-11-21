package com.omanfinance.candlebuffer.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CandleBackfillerTest {

  private CandleBackfiller candleBackfiller;
  private CandleRedisRepository redis;
  private MarketIngestion marketIngestion;

  @BeforeEach
  void setUp() {
    redis = mock(CandleRedisRepository.class);
    marketIngestion = mock(MarketIngestion.class);
    candleBackfiller = new CandleBackfiller(redis, marketIngestion);
  }

  @Test
  void fetchAndSaveCandles_calls_marketIngestion_and_redis() {
    when(marketIngestion.getBars(any()))
        .thenReturn(new BarsResponse(Map.of("AAPL", List.of()), null));
    candleBackfiller.fetchAndSaveCandles("AAPL", 60);

    verify(marketIngestion).getBars(any());
    verify(redis).saveCandles(any());
  }

  @Test
  void fetchAndSaveCandles_drops_malformed_candles() {
    Instant t = Instant.parse("2026-11-06T15:00:00Z");
    Bar valid =
        new Bar(
            null,
            t,
            BigDecimal.TEN,
            BigDecimal.TWO,
            BigDecimal.ONE,
            new BigDecimal("1.5"),
            100L,
            1L,
            null);
    Bar missingClose =
        new Bar(null, t, BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ONE, null, 200L, 1L, null);
    Bar missingTimestamp =
        new Bar(
            null,
            null,
            BigDecimal.ONE,
            BigDecimal.TWO,
            BigDecimal.ONE,
            BigDecimal.ONE,
            300L,
            1L,
            null);

    String symbol = "TSLA";
    BarsResponse response =
        new BarsResponse(Map.of(symbol, List.of(valid, missingClose, missingTimestamp)), null);
    when(marketIngestion.getBars(any())).thenReturn(response);

    List<Candle> candles = candleBackfiller.fetchAndSaveCandles(symbol, 60);
    assertTrue(candles.size() == 1);
    assertEquals(
        candles.getFirst().open(), BigDecimal.TEN); // only Bar valid had .open == BigDecimal.TEN
  }
}
