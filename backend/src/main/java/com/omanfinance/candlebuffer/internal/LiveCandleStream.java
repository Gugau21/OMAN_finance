package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Bar;
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
class LiveCandleStream {

  private static final Logger log = LoggerFactory.getLogger(LiveCandleStream.class);

  private MarketIngestion marketIngestion;
  private CandleRedisRepository redis;

  private Disposable subscription;

  /** Late subscribers only see candles emitted after they subscribe. */
  private final Many<Candle> candleSink = Sinks.many().multicast().onBackpressureBuffer();

  public LiveCandleStream(MarketIngestion marketIngestion, CandleRedisRepository redis) {
    this.marketIngestion = marketIngestion;
    this.redis = redis;
  }

  Flux<Candle> streamCandles() {
    return candleSink.asFlux();
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(2)
  void start() {
    subscription =
        marketIngestion
            .streamBars()
            .subscribe(this::onBar, err -> log.error("streamBars() failed", err));
  }

  @PreDestroy
  void stop() {
    if (subscription != null) {
      subscription.dispose();
    }
  }

  private void onBar(Bar bar) {
    Candle candle = Candle.fromBar(bar);
    try {
      redis.saveCandle(candle);
    } catch (CandleRedisRuntimeException e) {
      log.warn("failed to save candle for {}", candle.symbol(), e);
      return;
    }

    Sinks.EmitResult result = candleSink.tryEmitNext(candle);
    if (result.isFailure()) {
      log.warn("failed to emit candle for {}: {}", candle.symbol(), result);
    }
  }
}
