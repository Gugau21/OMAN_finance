package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.util.List;
import reactor.core.publisher.Flux;

public class CandleBackfillerService implements CandleBuffer {
  private final CandleHistory candleHistory;
  private final StockStatCalculator stockStatCalculator;

  public CandleBackfillerService(
      CandleHistory candleHistory, StockStatCalculator stockStatCalculator) {
    this.candleHistory = candleHistory;
    this.stockStatCalculator = stockStatCalculator;
  }

  @Override
  public Flux<Candle> streamCandles() {
    throw new UnsupportedOperationException("streamCandles is not implemented in this iteration");
  }

  @Override
  public List<Candle> getLastCandles(String symbol, int numberOfCandles) {
    return candleHistory.getLastCandles(symbol, numberOfCandles);
  }

  @Override
  public Flux<StockStat> streamStockStat() {
    throw new UnsupportedOperationException("streamStockStat is not implemented in this iteration");
  }

  @Override
  public List<PricePoint> getPricePoints(String symbol, int numberOfPricePoints) {
    return stockStatCalculator.getPricePoints(symbol, numberOfPricePoints);
  }

  @Override
  public StockStat getStockStat(String symbol, int windowSize) {
    return stockStatCalculator.getStockStat(symbol, windowSize);
  }
}
