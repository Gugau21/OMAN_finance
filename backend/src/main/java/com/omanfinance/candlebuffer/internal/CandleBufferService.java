package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class CandleBufferService implements CandleBuffer {
  private final CandleHistory candleHistory;
  private final StockStatCalculator stockStatCalculator;
  private final CandleBufferStartup candleBufferStartup;
  private final LiveCandleStream liveCandleStream;

  public CandleBufferService(
      CandleHistory candleHistory,
      StockStatCalculator stockStatCalculator,
      CandleBufferStartup candleBufferStartup,
      LiveCandleStream liveCandleStream) {
    this.candleHistory = candleHistory;
    this.stockStatCalculator = stockStatCalculator;
    this.candleBufferStartup = candleBufferStartup;
    this.liveCandleStream = liveCandleStream;
  }

  @Override
  public boolean isReady() {
    return candleBufferStartup.isReady();
  }

  @Override
  public Flux<Candle> streamCandles() {
    return liveCandleStream.streamCandles();
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
