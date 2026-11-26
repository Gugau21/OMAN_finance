package com.omanfinance.marketfeed.web.dto;

import com.omanfinance.marketmetrics.api.dto.PricePoint;
import java.math.BigDecimal;
import java.time.Instant;

/** REST/STOMP sparkline point; omits symbol (parent or topic carries it). */
public record ContractPricePoint(Instant timestamp, BigDecimal price) {

  public static ContractPricePoint from(PricePoint point) {
    return new ContractPricePoint(point.timestamp(), point.price());
  }
}
