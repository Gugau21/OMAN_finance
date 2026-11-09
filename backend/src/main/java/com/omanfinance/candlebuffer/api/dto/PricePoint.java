package com.omanfinance.candlebuffer.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Close price at the start of a minute */
public record PricePoint(Instant timestamp, BigDecimal price) {

  public static PricePoint fromCandle(Candle candle) {
    return new PricePoint(candle.timestamp(), candle.close());
  }
}
