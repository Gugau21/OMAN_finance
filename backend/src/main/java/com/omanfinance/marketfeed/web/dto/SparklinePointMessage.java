package com.omanfinance.marketfeed.web.dto;

import com.omanfinance.candlebuffer.api.dto.PricePoint;
import java.math.BigDecimal;
import java.time.Instant;

/** One sparkline point published to the stock WebSocket topic. */
public record SparklinePointMessage(String symbol, Instant timestamp, BigDecimal price) {

  public static SparklinePointMessage from(PricePoint point) {
    return new SparklinePointMessage(point.symbol(), point.timestamp(), point.price());
  }
}
