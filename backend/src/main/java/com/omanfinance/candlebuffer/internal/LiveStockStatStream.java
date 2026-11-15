package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

@Component
class LiveStockStatStream {
  private LiveCandleStream liveCandleStream;
  private StockStatCalculator stockStatCalculator;

  private static final Logger log = LoggerFactory.getLogger(LiveCandleStream.class);

  /** Late subscribers only see candles emitted after they subscribe. */
  private final Many<StockStat> stockStatSink = Sinks.many().multicast().onBackpressureBuffer();

  private final Many<PricePoint> pricePointSink = Sinks.many().multicast().onBackpressureBuffer();

  private Disposable subscription;

  public LiveStockStatStream(
      LiveCandleStream liveCandleStream, StockStatCalculator stockStatCalculator) {
    this.liveCandleStream = liveCandleStream;
    this.stockStatCalculator = stockStatCalculator;
  }

  /**
   * Call {@link #ensureStreamStockStatStarted(int)} once before subscribing here, or the flux stays
   * idle. Live stats emitted when candles are received. Late subscribers only receive stats from
   * after they subscribe; nothing is replayed.
   */
  public Flux<StockStat> streamStockStat() {
    return stockStatSink.asFlux();
  }

  /**
   * Call {@link #ensureStreamStockStatStarted(int)} once before subscribing here, or the flux stays
   * idle. Live price points emitted when candles are received. Late subscribers only receive stats
   * from after they subscribe; nothing is replayed.
   */
  public Flux<PricePoint> streamPricePoint() {
    return pricePointSink.asFlux();
  }

  /**
   * Subscribes to {@link LiveCandleStream#streamCandles()} and pushes a new {@link StockStat} on
   * {@link #streamStockStat()} after each saved candle. Call once from marketfeed before any
   * subscriber attaches to {@link #streamStockStat()}.
   *
   * @param windowSize how many most recent stored candles per symbol {@link StockStatCalculator}
   *     reads when recomputing.
   */
  public void ensureStreamStockStatStarted(int windowSize) {
    start(windowSize);
  }

  void start(int windowSize) {
    subscription =
        liveCandleStream
            .streamCandles()
            .subscribe(
                (candle) -> onCandle(candle, windowSize),
                err -> log.error("streamStockStat() failed", err));
  }

  @PreDestroy
  void stop() {
    if (subscription != null) {
      subscription.dispose();
    }
  }

  private void onCandle(Candle candle, int windowSize) {
    emitStockStat(candle, windowSize);
    emitPricePoint(candle);
  }

  private void emitStockStat(Candle candle, int windowSize) {
    StockStat stat = stockStatCalculator.getStockStat(candle.symbol(), windowSize);
    if (stat == null) {
      log.warn("no stockStat for {}", candle.symbol());
      return;
    }
    Sinks.EmitResult result = stockStatSink.tryEmitNext(stat);
    if (result.isFailure()) {
      log.warn("failed to emit stockStat for {}: {}", candle.symbol(), result);
    }
  }

  private void emitPricePoint(Candle candle) {
    PricePoint point = PricePoint.fromCandle(candle);
    Sinks.EmitResult result = pricePointSink.tryEmitNext(point);
    if (result.isFailure()) {
      log.warn("failed to emit pricePoint for {}: {}", candle.symbol(), result);
    }
  }
}
