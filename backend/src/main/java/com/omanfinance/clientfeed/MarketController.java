package com.omanfinance.clientfeed;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The {@code Market} endpoints of {@code api_contract.yaml}. */
@RestController
@RequestMapping("/api/market")
public class MarketController {

  private final MarketIngestion marketIngestion;

  public MarketController(MarketIngestion marketIngestion) {
    this.marketIngestion = marketIngestion;
  }

  /** The last Alpaca {@code /v2/clock} response. */
  @GetMapping("/status")
  public MarketStatusResponse getMarketStatus() {
    return MarketStatusResponse.from(marketIngestion.getMarketStatus());
  }

  /**
   * {@code MarketStatus} in {@code api_contract.yaml}. {@link Clock} keeps Alpaca's snake_case
   * names for reading {@code /v2/clock}, so it is not returned directly.
   */
  public record MarketStatusResponse(
      Instant timestamp, boolean isOpen, Instant nextOpen, Instant nextClose) {

    static MarketStatusResponse from(Clock clock) {
      return new MarketStatusResponse(
          clock.timestamp(), clock.isOpen(), clock.nextOpen(), clock.nextClose());
    }
  }
}
