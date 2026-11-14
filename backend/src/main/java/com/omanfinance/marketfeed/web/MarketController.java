package com.omanfinance.marketfeed.web;

import com.omanfinance.marketfeed.web.dto.MarketStatus;
import com.omanfinance.marketingestion.api.MarketIngestion;
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
  public MarketStatus getMarketStatus() {
    return MarketStatus.from(marketIngestion.getMarketStatus());
  }
}
