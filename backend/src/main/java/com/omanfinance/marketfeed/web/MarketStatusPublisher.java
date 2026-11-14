package com.omanfinance.marketfeed.web;

import com.omanfinance.marketfeed.web.dto.MarketStatus;
import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Clock;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.Disposables;

/**
 * Publishes every market status from {@link MarketIngestion#streamMarketStatus()} to {@value
 * #DESTINATION}, with the same payload as {@code GET /api/market/status} ({@code
 * stomp_contract.yaml}).
 *
 * <p>Nothing is replayed to a client that subscribes later; it gets the current status from REST.
 */
@Component
public class MarketStatusPublisher {

  static final String DESTINATION = "/topic/market/status";

  private static final Logger log = LoggerFactory.getLogger(MarketStatusPublisher.class);

  private final MarketIngestion marketIngestion;
  private final SimpMessageSendingOperations messaging;

  private Disposable subscription = Disposables.disposed();

  public MarketStatusPublisher(
      MarketIngestion marketIngestion, SimpMessageSendingOperations messaging) {
    this.marketIngestion = marketIngestion;
    this.messaging = messaging;
  }

  /**
   * Subscribes to the market status once the application is ready, because the broker drops
   * messages until it has started. The first status, the current one, goes to whoever is already
   * subscribed, usually no one.
   */
  @EventListener(ApplicationReadyEvent.class)
  void start() {
    subscription =
        marketIngestion
            .streamMarketStatus()
            .subscribe(
                this::publish,
                e -> log.error("Market status stream failed, publishing stopped", e));
  }

  /** Stops publishing when the application shuts down. */
  @PreDestroy
  void stop() {
    subscription.dispose();
  }

  /**
   * Sends {@code clock} to {@value #DESTINATION}. A failed send (delivery or JSON conversion) is
   * logged and skipped, because an exception here would cancel the subscription and stop every
   * later status.
   */
  void publish(Clock clock) {
    try {
      messaging.convertAndSend(DESTINATION, MarketStatus.from(clock));
    } catch (MessagingException e) {
      log.warn("Could not publish market status {}", clock, e);
    }
  }
}
