package com.omanfinance.marketfeed.web.dto;

import com.omanfinance.candlebuffer.api.dto.PricePoint;
import java.math.BigDecimal;
import java.time.Instant;

/** One sparkline point in the REST contract (no symbol field). */
public record ContractPricePoint(Instant timestamp, BigDecimal price) {

  public static ContractPricePoint from(PricePoint point) {
    return new ContractPricePoint(point.timestamp(), point.price());
  }
}
