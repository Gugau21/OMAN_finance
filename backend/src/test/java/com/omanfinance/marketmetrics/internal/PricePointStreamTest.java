package com.omanfinance.marketmetrics.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketmetrics.api.dto.PricePoint;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PricePointStreamTest {

  private static final Instant CANDLE_TIME = Instant.parse("2026-11-06T15:00:00Z");

  private CandleBuffer candleBuffer;
  private PricePointStream pricePointStream;

  @BeforeEach
  void setUp() {
    candleBuffer = mock(CandleBuffer.class);
    pricePointStream = new PricePointStream(candleBuffer);
  }

  @AfterEach
  void tearDown() {
    pricePointStream.stop();
  }

  @Test
  void streamPricePoints_emitsPointFromCandle() {
    Candle candle = candle("AAPL");
    PricePoint expected = PricePoint.fromCandle(candle);
    when(candleBuffer.streamCandles()).thenReturn(Flux.just(candle));

    StepVerifier.create(pricePointStream.streamPricePoints())
        .then(pricePointStream::start)
        .expectNext(expected)
        .thenCancel()
        .verify();

    verify(candleBuffer).streamCandles();
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
}
