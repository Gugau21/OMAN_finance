package com.omanfinance.marketfeed.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.marketfeed.web.dto.MarketStatus;
import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import reactor.core.publisher.Sinks;

class MarketStatusPublisherTest {

  private static final Clock OPEN =
      new Clock(
          Instant.parse("2026-11-09T15:15:30Z"),
          true,
          Instant.parse("2026-11-10T14:30:00Z"),
          Instant.parse("2026-11-09T21:00:00Z"));

  private static final Clock CLOSED =
      new Clock(
          Instant.parse("2026-11-09T21:00:05Z"),
          false,
          Instant.parse("2026-11-10T14:30:00Z"),
          Instant.parse("2026-11-10T21:00:00Z"));

  private final Sinks.Many<Clock> statuses = Sinks.many().multicast().directBestEffort();
  private final SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);

  private MarketStatusPublisher publisher;

  @BeforeEach
  void startPublisher() {
    MarketIngestion marketIngestion = mock(MarketIngestion.class);
    when(marketIngestion.streamMarketStatus()).thenReturn(statuses.asFlux());
    publisher = new MarketStatusPublisher(marketIngestion, messaging);
    publisher.start();
  }

  @Test
  void publishesEveryStatusInOrderAsTheRestPayload() {
    statuses.tryEmitNext(OPEN);
    statuses.tryEmitNext(CLOSED);

    InOrder inOrder = inOrder(messaging);
    inOrder.verify(messaging).convertAndSend("/topic/market/status", MarketStatus.from(OPEN));
    inOrder.verify(messaging).convertAndSend("/topic/market/status", MarketStatus.from(CLOSED));
  }

  @Test
  void keepsPublishingAfterAFailedSend() {
    doThrow(new MessageDeliveryException("broker down"))
        .when(messaging)
        .convertAndSend("/topic/market/status", MarketStatus.from(OPEN));

    statuses.tryEmitNext(OPEN);
    statuses.tryEmitNext(CLOSED);

    verify(messaging).convertAndSend("/topic/market/status", MarketStatus.from(CLOSED));
  }

  @Test
  void stopsPublishingOnShutdown() {
    publisher.stop();

    statuses.tryEmitNext(OPEN);

    verify(messaging, never()).convertAndSend(eq("/topic/market/status"), any(Object.class));
  }
}
