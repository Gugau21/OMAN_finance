package com.omanfinance.marketfeed.web;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.Disposables;

/**
 * Publishes every live candle from {@link
 * com.omanfinance.candlebuffer.api.CandleBuffer#streamCandles()} to {@code
 * /topic/stocks/{symbol}/candles}.
 *
 * <p>One message per stock per minute. Nothing is replayed to a client that subscribes later; it
 * loads candles from REST first.
 */
@Component
class CandlePublisher {

  private final String DESTINATION_PREFIX = "/topic/stocks/";
  private final String DESTINATION_SUFFIX = "/candles";

  private static final Logger log = LoggerFactory.getLogger(CandlePublisher.class);

  private final CandleBuffer candleBuffer;
  private final SimpMessageSendingOperations messaging;

  private Disposable subscription = Disposables.disposed();

  private static final int START_AFTER_LIVE_CANDLE_STREAM = 3;

  public CandlePublisher(CandleBuffer candleBuffer, SimpMessageSendingOperations messaging) {
    this.candleBuffer = candleBuffer;
    this.messaging = messaging;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(START_AFTER_LIVE_CANDLE_STREAM)
  void start() {
    subscription =
        candleBuffer
            .streamCandles()
            .subscribe(
                this::publish, e -> log.error("Candle stream failed, publishing stopped", e));
  }

  @PreDestroy
  void stop() {
    subscription.dispose();
  }

  void publish(Candle candle) {
    try {
      messaging.convertAndSend(DESTINATION_PREFIX + candle.symbol() + DESTINATION_SUFFIX, candle);
    } catch (MessagingException e) {
      log.warn("Could not publish candle of symbol: {}", candle.symbol(), e);
    }
  }
}
