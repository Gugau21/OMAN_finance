package com.omanfinance.candlebuffer.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class LiveStockStatStreamTest {

  private static final Instant CANDLE_TIME = Instant.parse("2026-11-06T15:00:00Z");
  private static final int WINDOW_SIZE = 5;

  private LiveCandleStream liveCandleStream;
  private StockStatCalculator stockStatCalculator;
  private LiveStockStatStream liveStockStatStream;

  @BeforeEach
  void setUp() {
    liveCandleStream = mock(LiveCandleStream.class);
    stockStatCalculator = mock(StockStatCalculator.class);
    liveStockStatStream = new LiveStockStatStream(liveCandleStream, stockStatCalculator);
  }

  @AfterEach
  void tearDown() {
    liveStockStatStream.stop();
  }

  @Test
  void ensureStreamStockStatStarted_emitsStatOnStreamStockStat() {
    Candle candle = candle("AAPL");
    StockStat expected = stockStat("AAPL");
    when(liveCandleStream.streamCandles()).thenReturn(Flux.just(candle));
    when(stockStatCalculator.getStockStat("AAPL", WINDOW_SIZE)).thenReturn(expected);

    StepVerifier.create(liveStockStatStream.streamStockStat())
        .then(() -> liveStockStatStream.ensureStreamStockStatStarted(WINDOW_SIZE))
        .expectNext(expected)
        .thenCancel()
        .verify();

    verify(stockStatCalculator).getStockStat("AAPL", WINDOW_SIZE);
  }

  @Test
  void streamStockStat_whenStatIsNull_doesNotEmit() {
    Candle candle = candle("AAPL");
    when(liveCandleStream.streamCandles()).thenReturn(Flux.just(candle));
    when(stockStatCalculator.getStockStat("AAPL", WINDOW_SIZE)).thenReturn(null);

    StepVerifier.create(liveStockStatStream.streamStockStat())
        .then(() -> liveStockStatStream.ensureStreamStockStatStarted(WINDOW_SIZE))
        .expectNoEvent(Duration.ofMillis(50))
        .thenCancel()
        .verify();

    verify(stockStatCalculator).getStockStat("AAPL", WINDOW_SIZE);
    verifyNoMoreInteractions(stockStatCalculator);
  }

  private static Candle candle(String symbol) {
    return new Candle(
        symbol,
        CANDLE_TIME,
        BigDecimal.ONE,
        BigDecimal.TWO,
        BigDecimal.ONE,
        new BigDecimal("1.5"),
        100L);
  }

  private static StockStat stockStat(String symbol) {
    return new StockStat(
        symbol, CANDLE_TIME, new BigDecimal("1.5"), new BigDecimal("0.5"), BigDecimal.TEN, 100L);
  }
}
