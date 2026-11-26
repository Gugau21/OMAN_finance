package com.omanfinance.marketmetrics.api.dto;

import com.omanfinance.candlebuffer.api.dto.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Close price of one minute for one stock. */
public record PricePoint(String symbol, Instant timestamp, BigDecimal price) {

  public PricePoint {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(price, "price");
  }

  public static PricePoint fromCandle(Candle candle) {
    return new PricePoint(candle.symbol(), candle.timestamp(), candle.close());
  }
}
