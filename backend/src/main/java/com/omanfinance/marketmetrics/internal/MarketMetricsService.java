package com.omanfinance.marketmetrics.internal;

import com.omanfinance.marketmetrics.api.MarketMetrics;
import com.omanfinance.marketmetrics.api.dto.PricePoint;
import com.omanfinance.marketmetrics.api.dto.StockStat;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
class MarketMetricsService implements MarketMetrics {

  private final StockStatCalculator stockStatCalculator;
  private final PricePointHistory pricePointHistory;
  private final LiveStockStatStream liveStockStatStream;
  private final PricePointStream pricePointStream;

  public MarketMetricsService(
      StockStatCalculator stockStatHistory,
      PricePointHistory pricePointHistory,
      LiveStockStatStream liveStockStatStream,
      PricePointStream pricePointStream) {
    this.stockStatCalculator = stockStatHistory;
    this.pricePointHistory = pricePointHistory;
    this.liveStockStatStream = liveStockStatStream;
    this.pricePointStream = pricePointStream;
  }

  @Override
  public Flux<StockStat> streamStockStats() {
    return liveStockStatStream.streamStockStats();
  }

  @Override
  public StockStat getStockStat(String symbol) {
    return stockStatCalculator.getStockStat(symbol);
  }

  @Override
  public Flux<PricePoint> streamPricePoints() {
    return pricePointStream.streamPricePoints();
  }

  @Override
  public List<PricePoint> getPricePoints(String symbol) {
    return pricePointHistory.getPricePoints(symbol);
  }
}
