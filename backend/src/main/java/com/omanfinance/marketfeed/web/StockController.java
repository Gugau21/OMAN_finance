package com.omanfinance.marketfeed.web;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The {@code Stocks} endpoints of {@code api_contract.yaml}. */
@RestController
@RequestMapping("/api/stocks")
public class StockController {

  private final MarketIngestion marketIngestion;

  public StockController(MarketIngestion marketIngestion) {
    this.marketIngestion = marketIngestion;
  }

  /**
   * Every tracked stock, in the order {@link MarketIngestion#getTrackedStocks()} returns them.
   * {@link Stock} already has the contract's {@code Stock} shape, so it is returned as it is.
   */
  @GetMapping
  public List<Stock> listStocks() {
    return marketIngestion.getTrackedStocks();
  }
}
