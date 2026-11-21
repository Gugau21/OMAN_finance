package com.omanfinance.marketfeed.web.dto;

import com.omanfinance.marketingestion.api.dto.Clock;
import java.time.Instant;

/**
 * {@code MarketStatus} in {@code api_contract.yaml}, also the payload of {@code
 * /topic/market/status} in {@code stomp_contract.yaml}. {@link Clock} keeps Alpaca's snake_case
 * names for reading {@code /v2/clock}, so it is not sent directly.
 */
public record MarketStatus(Instant timestamp, boolean isOpen, Instant nextOpen, Instant nextClose) {

  public static MarketStatus from(Clock clock) {
    return new MarketStatus(clock.timestamp(), clock.isOpen(), clock.nextOpen(), clock.nextClose());
  }
}
