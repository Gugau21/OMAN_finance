package com.omanfinance.candlebuffer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.dto.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class CandleBufferServiceTest {

  private CandleHistory candleHistory;
  private CandleBufferStartup candleBufferStartup;
  private LiveCandleStream liveCandleStream;
  private CandleBufferService service;

  @BeforeEach
  void setUp() {
    candleHistory = mock(CandleHistory.class);
    candleBufferStartup = mock(CandleBufferStartup.class);
    liveCandleStream = mock(LiveCandleStream.class);
    service = new CandleBufferService(candleHistory, candleBufferStartup, liveCandleStream);
  }

  @Test
  void isReady_delegates_to_startup() {
    when(candleBufferStartup.isReady()).thenReturn(true);

    assertThat(service.isReady()).isTrue();

    verify(candleBufferStartup).isReady();
  }

  @Test
  void getLastCandles_returns_what_history_returns() {
    List<Candle> candles = List.of(candle());
    when(candleHistory.getLastCandles("AAPL", 60)).thenReturn(candles);

    assertThat(service.getLastCandles("AAPL", 60)).isSameAs(candles);

    verify(candleHistory).getLastCandles("AAPL", 60);
  }

  @Test
  void getLastCandles_returns_empty_list_when_history_is_empty() {
    when(candleHistory.getLastCandles("AAPL", 60)).thenReturn(List.of());

    assertThat(service.getLastCandles("AAPL", 60)).isEmpty();

    verify(candleHistory).getLastCandles("AAPL", 60);
  }

  @Test
  void streamCandles_receives_candles() {
    Candle expected = candle();
    when(liveCandleStream.streamCandles()).thenReturn(Flux.just(expected));

    StepVerifier.create(service.streamCandles()).expectNext(expected).thenCancel().verify();

    verify(liveCandleStream).streamCandles();
  }

  private static Candle candle() {
    return new Candle(
        "AAPL",
        Instant.parse("2026-10-29T16:00:00Z"),
        new BigDecimal("100"),
        new BigDecimal("101"),
        new BigDecimal("100"),
        new BigDecimal("101"),
        10);
  }
}
