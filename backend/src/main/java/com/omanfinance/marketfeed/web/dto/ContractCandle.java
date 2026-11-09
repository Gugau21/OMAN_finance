package com.omanfinance.marketfeed.web.dto;

import com.omanfinance.candlebuffer.api.dto.Candle;
import java.math.BigDecimal;
import java.time.Instant;

/** One-minute candle in the REST contract (no symbol field). */
public record ContractCandle(
    Instant timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume) {

  public static ContractCandle from(Candle candle) {
    return new ContractCandle(
        candle.timestamp(),
        candle.open(),
        candle.high(),
        candle.low(),
        candle.close(),
        candle.volume());
  }
}
