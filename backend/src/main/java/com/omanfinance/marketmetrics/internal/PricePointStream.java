package com.omanfinance.marketmetrics.internal;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketmetrics.api.dto.PricePoint;
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
class PricePointStream {
  private static final Logger log = LoggerFactory.getLogger(PricePointStream.class);

  /** After {@link com.omanfinance.candlebuffer.internal.LiveCandleStream} (order 2). */
  private static final int START_AFTER_LIVE_CANDLE_STREAM = 3;

  private final CandleBuffer candleBuffer;

  /** Late subscribers only see points emitted after they subscribe. */
  private final Many<PricePoint> pricePointSink = Sinks.many().multicast().onBackpressureBuffer();

  private Disposable subscription;

  public PricePointStream(CandleBuffer candleBuffer) {
    this.candleBuffer = candleBuffer;
  }

  public Flux<PricePoint> streamPricePoints() {
    return pricePointSink.asFlux();
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(START_AFTER_LIVE_CANDLE_STREAM)
  void start() {
    subscription =
        candleBuffer
            .streamCandles()
            .subscribe(this::onCandle, err -> log.error("streamPricePoints() failed", err));
  }

  @PreDestroy
  void stop() {
    if (subscription != null) {
      subscription.dispose();
    }
  }

  private void onCandle(Candle candle) {
    PricePoint point = PricePoint.fromCandle(candle);
    Sinks.EmitResult result = pricePointSink.tryEmitNext(point);
    if (result.isFailure()) {
      log.warn("failed to emit pricePoint for {}: {}", candle.symbol(), result);
    }
  }
}
