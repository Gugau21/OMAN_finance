package com.omanfinance.candlebuffer.api.dto;

import com.omanfinance.marketingestion.api.dto.Bar;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Candle(
    String symbol,
    Instant timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume) {

  public Candle {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(open, "open");
    Objects.requireNonNull(high, "high");
    Objects.requireNonNull(low, "low");
    Objects.requireNonNull(close, "close");
  }

  /**
   * @throws NullPointerException if any required field on {@code bar} is null
   */
  public static Candle fromBar(Bar bar) {
    return new Candle(
        bar.symbol(),
        bar.timestamp(),
        bar.open(),
        bar.high(),
        bar.low(),
        bar.close(),
        bar.volume());
  }
}
