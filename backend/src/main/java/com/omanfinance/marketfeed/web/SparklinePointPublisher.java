package com.omanfinance.marketfeed.web;

import com.omanfinance.marketfeed.web.dto.SparklinePointMessage;
import com.omanfinance.marketmetrics.api.MarketMetrics;
import com.omanfinance.marketmetrics.api.dto.PricePoint;
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
 * Publishes every live sparkline point from {@link MarketMetrics#streamPricePoints()} to {@code
 * /topic/stocks/sparkline}.
 *
 * <p>One message per stock per minute. Nothing is replayed to a client that subscribes later; it
 * loads candles from REST first.
 */
@Component
class SparklinePointPublisher {
  private final String DESTINATION = "/topic/stocks/sparkline";

  /**
   * After live metrics streams ({@link com.omanfinance.marketmetrics.internal.PricePointStream}
   * order 3).
   */
  private static final int START_AFTER_METRICS_STREAMS = 4;

  private static final Logger log = LoggerFactory.getLogger(SparklinePointPublisher.class);

  private final MarketMetrics marketMetrics;
  private final SimpMessageSendingOperations messaging;

  private Disposable subscription = Disposables.disposed();

  public SparklinePointPublisher(
      MarketMetrics marketMetrics, SimpMessageSendingOperations messaging) {
    this.marketMetrics = marketMetrics;
    this.messaging = messaging;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(START_AFTER_METRICS_STREAMS)
  void start() {
    subscription =
        marketMetrics
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
      messaging.convertAndSend(DESTINATION, SparklinePointMessage.from(point));
    } catch (MessagingException e) {
      log.warn("Could not publish PricePoint of symbol: {}", point.symbol(), e);
    }
  }
}
