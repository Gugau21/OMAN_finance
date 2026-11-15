package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
class CandleBufferService implements CandleBuffer {
  private final CandleHistory candleHistory;
  private final StockStatCalculator stockStatCalculator;
  private final CandleBufferStartup candleBufferStartup;
  private final LiveCandleStream liveCandleStream;
  private final LiveStockStatStream liveStockStatStream;

  public CandleBufferService(
      CandleHistory candleHistory,
      StockStatCalculator stockStatCalculator,
      CandleBufferStartup candleBufferStartup,
      LiveCandleStream liveCandleStream,
      LiveStockStatStream liveStockStatStream) {
    this.candleHistory = candleHistory;
    this.stockStatCalculator = stockStatCalculator;
    this.candleBufferStartup = candleBufferStartup;
    this.liveCandleStream = liveCandleStream;
    this.liveStockStatStream = liveStockStatStream;
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
  public Flux<StockStat> streamStockStats() {
    return liveStockStatStream.streamStockStat();
  }

  @Override
  public Flux<PricePoint> streamPricePoints() {
    return liveStockStatStream.streamPricePoint();
  }

  @Override
  public void ensureStreamStockStatStarted(int windowSize) {
    liveStockStatStream.ensureStreamStockStatStarted(windowSize);
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
