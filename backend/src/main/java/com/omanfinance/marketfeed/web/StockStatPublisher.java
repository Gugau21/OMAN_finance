package com.omanfinance.marketfeed.web;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.StockStat;
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
 * Publishes every live stock stat from {@link
 * com.omanfinance.candlebuffer.api.CandleBuffer#streamStockStats()} to {@code /topic/stocks/stat}
 * and {@code /topic/stocks/{symbol}/stat}, with the same payload as one element of {@code GET
 * /api/stocks/stat} ({@code stomp_contract.yaml}).
 *
 * <p>One message per stock per minute on each destination. Nothing is replayed to a client that
 * subscribes later; it loads stats from REST first.
 */
@Component
class StockStatPublisher {

  private static final String ALL_STOCKS = "/topic/stocks/stat";
  private static final String SYMBOL_PREFIX = "/topic/stocks/";
  private static final String SYMBOL_SUFFIX = "/stat";

  /** After {@link com.omanfinance.marketfeed.StreamStartupSubscriber} (order 4). */
  private static final int START_AFTER_STREAM_STOCK_STAT_STARTED = 5;

  private static final Logger log = LoggerFactory.getLogger(StockStatPublisher.class);

  private final CandleBuffer candleBuffer;
  private final SimpMessageSendingOperations messaging;

  private Disposable subscription = Disposables.disposed();

  public StockStatPublisher(CandleBuffer candleBuffer, SimpMessageSendingOperations messaging) {
    this.candleBuffer = candleBuffer;
    this.messaging = messaging;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(START_AFTER_STREAM_STOCK_STAT_STARTED)
  void start() {
    subscription =
        candleBuffer
            .streamStockStats()
            .subscribe(
                this::publish, e -> log.error("Stock stat stream failed, publishing stopped", e));
  }

  @PreDestroy
  void stop() {
    subscription.dispose();
  }

  void publish(StockStat stat) {
    send(ALL_STOCKS, stat);
    send(SYMBOL_PREFIX + stat.symbol() + SYMBOL_SUFFIX, stat);
  }

  private void send(String destination, StockStat stat) {
    try {
      messaging.convertAndSend(destination, stat);
    } catch (MessagingException e) {
      log.warn("Could not publish stock stat for {} to {}", stat.symbol(), destination, e);
    }
  }
}
