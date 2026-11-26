package com.omanfinance.marketmetrics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.marketmetrics.api.dto.PricePoint;
import com.omanfinance.marketmetrics.api.dto.StockStat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class MarketMetricsServiceTest {

  private StockStatCalculator stockStatCalculator;
  private PricePointHistory pricePointHistory;
  private LiveStockStatStream liveStockStatStream;
  private PricePointStream pricePointStream;
  private MarketMetricsService service;

  @BeforeEach
  void setUp() {
    stockStatCalculator = mock(StockStatCalculator.class);
    pricePointHistory = mock(PricePointHistory.class);
    liveStockStatStream = mock(LiveStockStatStream.class);
    pricePointStream = mock(PricePointStream.class);
    service =
        new MarketMetricsService(
            stockStatCalculator, pricePointHistory, liveStockStatStream, pricePointStream);
  }

  @Test
  void getPricePoints_returns_what_calculator_returns() {
    List<PricePoint> points =
        List.of(
            new PricePoint("AAPL", Instant.parse("2026-10-29T16:00:00Z"), new BigDecimal("101")));
    when(pricePointHistory.getPricePoints("AAPL")).thenReturn(points);

    assertThat(service.getPricePoints("AAPL")).isSameAs(points);

    verify(pricePointHistory).getPricePoints("AAPL");
  }

  @Test
  void getPricePoints_returns_null_when_calculator_returns_null() {
    when(pricePointHistory.getPricePoints("AAPL")).thenReturn(null);

    assertThat(service.getPricePoints("AAPL")).isNull();

    verify(pricePointHistory).getPricePoints("AAPL");
  }

  @Test
  void getStockStat_returns_what_calculator_returns() {
    StockStat stat = stockStat();
    when(stockStatCalculator.getStockStat("AAPL")).thenReturn(stat);

    assertThat(service.getStockStat("AAPL")).isSameAs(stat);

    verify(stockStatCalculator).getStockStat("AAPL");
  }

  @Test
  void getStockStat_returns_null_when_calculator_returns_null() {
    when(stockStatCalculator.getStockStat("AAPL")).thenReturn(null);

    assertThat(service.getStockStat("AAPL")).isNull();

    verify(stockStatCalculator).getStockStat("AAPL");
  }

  @Test
  void streamStockStats_receives_stockStats() {
    StockStat expected = stockStat();
    when(liveStockStatStream.streamStockStats()).thenReturn(Flux.just(expected));

    StepVerifier.create(service.streamStockStats()).expectNext(expected).thenCancel().verify();

    verify(liveStockStatStream).streamStockStats();
  }

  @Test
  void streamPricePoints_receives_pricePoints() {
    PricePoint expected =
        new PricePoint("AAPL", Instant.parse("2026-10-29T16:00:00Z"), new BigDecimal("101"));
    when(pricePointStream.streamPricePoints()).thenReturn(Flux.just(expected));

    StepVerifier.create(service.streamPricePoints()).expectNext(expected).thenCancel().verify();

    verify(pricePointStream).streamPricePoints();
  }

  private static StockStat stockStat() {
    return new StockStat(
        "AAPL",
        Instant.parse("2026-10-29T16:04:00Z"),
        new BigDecimal("110"),
        new BigDecimal("10"),
        new BigDecimal("10"),
        150L);
  }
}
