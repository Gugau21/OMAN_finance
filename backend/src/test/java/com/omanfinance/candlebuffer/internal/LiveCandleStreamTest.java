package com.omanfinance.candlebuffer.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Bar;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class LiveCandleStreamTest {

  private static final Instant BAR_TIME = Instant.parse("2026-11-06T15:00:00Z");

  private MarketIngestion marketIngestion;
  private CandleRedisRepository redis;
  private LiveCandleStream liveCandleStream;

  @BeforeEach
  void setUp() {
    marketIngestion = mock(MarketIngestion.class);
    redis = mock(CandleRedisRepository.class);
    liveCandleStream = new LiveCandleStream(marketIngestion, redis);
  }

  @AfterEach
  void tearDown() {
    liveCandleStream.stop();
  }

  @Test
  void start_savesCandleAndEmitsOnStreamCandles() {
    Bar bar = bar("AAPL");
    Candle expected = Candle.fromBar(bar);
    when(marketIngestion.streamBars()).thenReturn(Flux.just(bar));

    StepVerifier.create(liveCandleStream.streamCandles())
        .then(liveCandleStream::start)
        .expectNext(expected)
        .thenCancel()
        .verify();

    verify(redis).saveCandle(expected);
  }

  @Test
  void start_whenSaveFails_doesNotEmit() {
    Bar bar = bar("AAPL");
    when(marketIngestion.streamBars()).thenReturn(Flux.just(bar));
    doThrow(new CandleRedisRuntimeException("redis down")).when(redis).saveCandle(any());

    StepVerifier.create(liveCandleStream.streamCandles())
        .then(liveCandleStream::start)
        .expectNoEvent(Duration.ofMillis(50))
        .thenCancel()
        .verify();

    verify(redis).saveCandle(any());
    verifyNoMoreInteractions(redis);
  }

  private static Bar bar(String symbol) {
    return new Bar(
        symbol,
        BAR_TIME,
        BigDecimal.ONE,
        BigDecimal.TWO,
        BigDecimal.ONE,
        new BigDecimal("1.5"),
        100L,
        1L,
        null);
  }
}
