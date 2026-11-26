package com.omanfinance.marketmetrics.internal;

import static com.omanfinance.marketmetrics.internal.MarketMetricsConstants.CANDLE_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketmetrics.api.dto.StockStat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockStatCalculatorTest {

  private CandleBuffer candleBuffer;
  private StockStatCalculator calculator;

  @BeforeEach
  void setUp() {
    candleBuffer = mock(CandleBuffer.class);
    calculator = new StockStatCalculator(candleBuffer);
  }

  @Test
  void getStockStat_calculates_from_five_candles() {
    when(candleBuffer.getLastCandles("AAPL", CANDLE_WINDOW)).thenReturn(fiveCandles());

    StockStat stat = calculator.getStockStat("AAPL");

    // oldest open 100, latest close 110 → change 10, changePercent 10
    // volumes 10+20+30+40+50 = 150
    assertThat(stat.symbol()).isEqualTo("AAPL");
    assertThat(stat.timestamp()).isEqualTo(Instant.parse("2026-10-29T16:04:00Z"));
    assertThat(stat.price()).isEqualByComparingTo("110");
    assertThat(stat.change()).isEqualByComparingTo("10");
    assertThat(stat.changePercent()).isEqualByComparingTo("10");
    assertThat(stat.volume()).isEqualTo(150L);
  }

  @Test
  void getStockStat_returns_null_when_no_candles() {
    when(candleBuffer.getLastCandles("AAPL", CANDLE_WINDOW)).thenReturn(List.of());

    assertThat(calculator.getStockStat("AAPL")).isNull();
  }

  private static List<Candle> fiveCandles() {
    return List.of(
        candle("2026-10-29T16:00:00Z", "100", "101", 10),
        candle("2026-10-29T16:01:00Z", "101", "102", 20),
        candle("2026-10-29T16:02:00Z", "102", "103", 30),
        candle("2026-10-29T16:03:00Z", "103", "104", 40),
        candle("2026-10-29T16:04:00Z", "104", "110", 50));
  }

  private static Candle candle(String timestamp, String open, String close, long volume) {
    return new Candle(
        "AAPL",
        Instant.parse(timestamp),
        new BigDecimal(open),
        new BigDecimal(close), // high not important
        new BigDecimal(open), // low not important
        new BigDecimal(close),
        volume);
  }
}
