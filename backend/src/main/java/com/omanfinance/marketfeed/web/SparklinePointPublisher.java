package com.omanfinance.marketfeed.web;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.marketfeed.web.dto.ContractPricePoint;
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
 * Publishes every live sparkline point from {@link
 * com.omanfinance.candlebuffer.api.CandleBuffer#streamPricePoints()} to {@code
 * /topic/stocks/{symbol}/candles}.
 *
 * <p>One message per stock per minute. Nothing is replayed to a client that subscribes later; it
 * loads candles from REST first.
 */
@Component
class SparklinePointPublisher {
  private final String DESTINATION = "/topic/stocks/sparkline";

  /** After {@link com.omanfinance.marketfeed.StreamStartupSubscriber} (order 4). */
  private static final int START_AFTER_STREAM_STOCK_STAT_STARTED = 5;

  private static final Logger log = LoggerFactory.getLogger(CandlePublisher.class);

  private final CandleBuffer candleBuffer;
  private final SimpMessageSendingOperations messaging;

  private Disposable subscription = Disposables.disposed();

  public SparklinePointPublisher(
      CandleBuffer candleBuffer, SimpMessageSendingOperations messaging) {
    this.candleBuffer = candleBuffer;
    this.messaging = messaging;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(START_AFTER_STREAM_STOCK_STAT_STARTED)
  void start() {
    subscription =
        candleBuffer
            .streamPricePoints()
            .subscribe(
                this::publish,
                e -> log.error("SparklinePoint stream failed, publishing stopped", e));
  }

  @PreDestroy
  void stop() {
    subscription.dispose();
  }

  void publish(PricePoint point) {
    try {
      messaging.convertAndSend(DESTINATION, ContractPricePoint.from(point));
    } catch (MessagingException e) {
      log.warn("Could not publish PricePoint of symbol: {}", point.symbol(), e);
    }
  }
}
