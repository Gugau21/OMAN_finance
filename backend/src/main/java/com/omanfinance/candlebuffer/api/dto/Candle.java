package com.omanfinance.candlebuffer.api.dto;

import com.omanfinance.marketingestion.api.dto.Bar;
import java.math.BigDecimal;
import java.time.Instant;

public record Candle(
    String symbol,
    Instant timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume) {

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
