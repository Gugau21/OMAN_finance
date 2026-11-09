package com.omanfinance.candlebuffer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandleBufferServiceTest {

  private CandleHistory candleHistory;
  private StockStatCalculator stockStatCalculator;
  private CandleBufferStartup candleBufferStartup;
  private CandleBackfillerService service;

  @BeforeEach
  void setUp() {
    candleHistory = mock(CandleHistory.class);
    stockStatCalculator = mock(StockStatCalculator.class);
    candleBufferStartup = mock(CandleBufferStartup.class);
    service = new CandleBackfillerService(candleHistory, stockStatCalculator, candleBufferStartup);
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
  void getPricePoints_returns_what_calculator_returns() {
    List<PricePoint> points =
        List.of(new PricePoint(Instant.parse("2026-10-29T16:00:00Z"), new BigDecimal("101")));
    when(stockStatCalculator.getPricePoints("AAPL", 60)).thenReturn(points);

    assertThat(service.getPricePoints("AAPL", 60)).isSameAs(points);

    verify(stockStatCalculator).getPricePoints("AAPL", 60);
  }

  @Test
  void getPricePoints_returns_null_when_calculator_returns_null() {
    when(stockStatCalculator.getPricePoints("AAPL", 60)).thenReturn(null);

    assertThat(service.getPricePoints("AAPL", 60)).isNull();

    verify(stockStatCalculator).getPricePoints("AAPL", 60);
  }

  @Test
  void getStockStat_returns_what_calculator_returns() {
    StockStat stat =
        new StockStat(
            "AAPL",
            Instant.parse("2026-10-29T16:04:00Z"),
            new BigDecimal("110"),
            new BigDecimal("10"),
            new BigDecimal("10"),
            150L);
    when(stockStatCalculator.getStockStat("AAPL", 60)).thenReturn(stat);

    assertThat(service.getStockStat("AAPL", 60)).isSameAs(stat);

    verify(stockStatCalculator).getStockStat("AAPL", 60);
  }

  @Test
  void getStockStat_returns_null_when_calculator_returns_null() {
    when(stockStatCalculator.getStockStat("AAPL", 60)).thenReturn(null);

    assertThat(service.getStockStat("AAPL", 60)).isNull();

    verify(stockStatCalculator).getStockStat("AAPL", 60);
  }

  @Test
  void streamCandles_is_not_implemented() {
    assertThatThrownBy(service::streamCandles)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("streamCandles");
  }

  @Test
  void streamStockStat_is_not_implemented() {
    assertThatThrownBy(service::streamStockStat)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("streamStockStat");
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
