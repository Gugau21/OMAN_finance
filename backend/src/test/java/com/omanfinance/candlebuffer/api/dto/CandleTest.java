package com.omanfinance.candlebuffer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.omanfinance.marketingestion.api.dto.Bar;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CandleTest {

  private static final Instant T = Instant.parse("2026-11-06T15:00:00Z");

  @Test
  void fromBar_copiesSymbol_and_OHLCV_from_bar() {
    Bar bar =
        new Bar(
            "AAPL",
            T,
            new BigDecimal("10"),
            new BigDecimal("11"),
            new BigDecimal("9"),
            new BigDecimal("10.5"),
            1000L,
            42L,
            new BigDecimal("10.2"));

    Candle candle = Candle.fromBar(bar);

    assertThat(candle.symbol()).isEqualTo("AAPL");
    assertThat(candle.timestamp()).isEqualTo(T);
    assertThat(candle.open()).isEqualByComparingTo("10");
    assertThat(candle.high()).isEqualByComparingTo("11");
    assertThat(candle.low()).isEqualByComparingTo("9");
    assertThat(candle.close()).isEqualByComparingTo("10.5");
    assertThat(candle.volume()).isEqualTo(1000L);
  }

  @Test
  void fromBar_uses_symbol_from_bar_withSymbol() {
    Bar bar =
        new Bar(
                null,
                T,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                1L,
                0L,
                BigDecimal.ONE)
            .withSymbol("MSFT");

    Candle candle = Candle.fromBar(bar);

    assertThat(candle.symbol()).isEqualTo("MSFT");
  }
}
