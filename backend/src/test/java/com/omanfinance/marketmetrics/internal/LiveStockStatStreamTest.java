package com.omanfinance.marketmetrics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketmetrics.api.dto.StockStat;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class LiveStockStatStreamTest {

  private static final Instant CANDLE_TIME = Instant.parse("2026-11-06T15:00:00Z");

  private CandleBuffer candleBuffer;
  private StockStatCalculator stockStatCalculator;
  private LiveStockStatStream liveStockStatStream;

  @BeforeEach
  void setUp() {
    candleBuffer = mock(CandleBuffer.class);
    stockStatCalculator = mock(StockStatCalculator.class);
    liveStockStatStream = new LiveStockStatStream(candleBuffer, stockStatCalculator);
  }

  @AfterEach
  void tearDown() {
    liveStockStatStream.stop();
  }

  @Test
  void start_receivesCandleFromStreamCandles() {
    Candle candle = candle("AAPL");
    AtomicReference<Candle> received = new AtomicReference<>();
    when(candleBuffer.streamCandles()).thenReturn(Flux.just(candle).doOnNext(received::set));
    when(stockStatCalculator.getStockStat("AAPL")).thenReturn(stockStat("AAPL"));

    liveStockStatStream.start();

    verify(candleBuffer).streamCandles();
    assertThat(received).hasValue(candle);
  }

  @Test
  void streamStockStats_emitsExpectedStockStat() {
    Candle candle = candle("AAPL");
    StockStat expected = stockStat("AAPL");
    when(candleBuffer.streamCandles()).thenReturn(Flux.just(candle));
    when(stockStatCalculator.getStockStat("AAPL")).thenReturn(expected);

    StepVerifier.create(liveStockStatStream.streamStockStats())
        .then(liveStockStatStream::start)
        .expectNext(expected)
        .thenCancel()
        .verify();

    verify(stockStatCalculator).getStockStat("AAPL");
  }

  @Test
  void streamStockStats_whenStatIsNull_doesNotEmit() {
    Candle candle = candle("AAPL");
    when(candleBuffer.streamCandles()).thenReturn(Flux.just(candle));
    when(stockStatCalculator.getStockStat("AAPL")).thenReturn(null);

    StepVerifier.create(liveStockStatStream.streamStockStats())
        .then(liveStockStatStream::start)
        .expectNoEvent(Duration.ofMillis(50))
        .thenCancel()
        .verify();

    verify(stockStatCalculator).getStockStat("AAPL");
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
