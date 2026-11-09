package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class CandleBackfillerService implements CandleBuffer {
  private final CandleHistory candleHistory;
  private final StockStatCalculator stockStatCalculator;
  private final CandleBufferStartup candleBufferStartup;

  public CandleBackfillerService(
      CandleHistory candleHistory,
      StockStatCalculator stockStatCalculator,
      CandleBufferStartup candleBufferStartup) {
    this.candleHistory = candleHistory;
    this.stockStatCalculator = stockStatCalculator;
    this.candleBufferStartup = candleBufferStartup;
  }

  @Override
  public boolean isReady() {
    return candleBufferStartup.isReady();
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
