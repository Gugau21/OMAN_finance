package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
class CandleBufferService implements CandleBuffer {
  private final CandleHistory candleHistory;
  private final CandleBufferStartup candleBufferStartup;
  private final LiveCandleStream liveCandleStream;

  public CandleBufferService(
      CandleHistory candleHistory,
      CandleBufferStartup candleBufferStartup,
      LiveCandleStream liveCandleStream) {
    this.candleHistory = candleHistory;
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
}
