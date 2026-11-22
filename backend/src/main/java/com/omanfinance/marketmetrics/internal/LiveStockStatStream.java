package com.omanfinance.marketmetrics.internal;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketmetrics.api.dto.StockStat;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

@Component
class LiveStockStatStream {
  private static final Logger log = LoggerFactory.getLogger(LiveStockStatStream.class);

  /** After {@link com.omanfinance.candlebuffer.internal.LiveCandleStream} (order 2). */
  private static final int START_AFTER_LIVE_CANDLE_STREAM = 3;

  private final CandleBuffer candleBuffer;
  private final StockStatCalculator stockStatCalculator;

  /** Late subscribers only see stats emitted after they subscribe. */
  private final Many<StockStat> stockStatSink = Sinks.many().multicast().onBackpressureBuffer();

  private Disposable subscription;

  public LiveStockStatStream(CandleBuffer candleBuffer, StockStatCalculator stockStatCalculator) {
    this.candleBuffer = candleBuffer;
    this.stockStatCalculator = stockStatCalculator;
  }

  public Flux<StockStat> streamStockStats() {
    return stockStatSink.asFlux();
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(START_AFTER_LIVE_CANDLE_STREAM)
  void start() {
    subscription =
        candleBuffer
            .streamCandles()
            .subscribe(this::onCandle, err -> log.error("streamStockStats() failed", err));
  }

  @PreDestroy
  void stop() {
    if (subscription != null) {
      subscription.dispose();
    }
  }

  private void onCandle(Candle candle) {
    StockStat stat = stockStatCalculator.getStockStat(candle.symbol());
    if (stat == null) {
      log.warn("no stockStat for {}", candle.symbol());
      return;
    }
    Sinks.EmitResult result = stockStatSink.tryEmitNext(stat);
    if (result.isFailure()) {
      log.warn("failed to emit stockStat for {}: {}", candle.symbol(), result);
    }
  }
}
