package com.omanfinance.candlebuffer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockStatCalculatorTest {

  private CandleHistory candleHistory;
  private StockStatCalculator calculator;

  @BeforeEach
  void setUp() {
    candleHistory = mock(CandleHistory.class);
    calculator = new StockStatCalculator(candleHistory);
  }

  @Test
  void getStockStat_calculates_from_five_candles() {
    when(candleHistory.getLastCandles("AAPL", 5)).thenReturn(fiveCandles());

    StockStat stat = calculator.getStockStat("AAPL", 5);

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
    when(candleHistory.getLastCandles("AAPL", 60)).thenReturn(List.of());

    assertThat(calculator.getStockStat("AAPL", 60)).isNull();
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
