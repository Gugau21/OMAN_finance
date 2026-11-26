package com.omanfinance.marketfeed.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.marketmetrics.api.MarketMetrics;
import com.omanfinance.marketmetrics.api.dto.StockStat;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import reactor.core.publisher.Sinks;

class StockStatPublisherTest {

  private static final Instant MINUTE = Instant.parse("2026-11-09T17:07:00Z");

  private static final StockStat AAPL = stat("AAPL", "231.42");
  private static final StockStat MSFT = stat("MSFT", "420.10");

  private final Sinks.Many<StockStat> stats = Sinks.many().multicast().directBestEffort();
  private final SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);

  private StockStatPublisher publisher;

  @BeforeEach
  void startPublisher() {
    MarketMetrics marketMetrics = mock(MarketMetrics.class);
    when(marketMetrics.streamStockStats()).thenReturn(stats.asFlux());
    publisher = new StockStatPublisher(marketMetrics, messaging);
    publisher.start();
  }

  @Test
  void publishesEveryStatInOrderToBothTopics() {
    stats.tryEmitNext(AAPL);
    stats.tryEmitNext(MSFT);

    InOrder inOrder = inOrder(messaging);
    inOrder.verify(messaging).convertAndSend("/topic/stocks/stat", AAPL);
    inOrder.verify(messaging).convertAndSend("/topic/stocks/AAPL/stat", AAPL);
    inOrder.verify(messaging).convertAndSend("/topic/stocks/stat", MSFT);
    inOrder.verify(messaging).convertAndSend("/topic/stocks/MSFT/stat", MSFT);
  }

  @Test
  void keepsPublishingAfterAFailedSend() {
    doThrow(new MessageDeliveryException("broker down"))
        .when(messaging)
        .convertAndSend("/topic/stocks/stat", AAPL);

    stats.tryEmitNext(AAPL);
    stats.tryEmitNext(MSFT);

    InOrder inOrder = inOrder(messaging);
    inOrder.verify(messaging).convertAndSend("/topic/stocks/AAPL/stat", AAPL);
    inOrder.verify(messaging).convertAndSend("/topic/stocks/stat", MSFT);
    inOrder.verify(messaging).convertAndSend("/topic/stocks/MSFT/stat", MSFT);
  }

  @Test
  void stopsPublishingOnShutdown() {
    publisher.stop();

    stats.tryEmitNext(AAPL);

    verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
  }

  private static StockStat stat(String symbol, String price) {
    return new StockStat(
        symbol,
        MINUTE,
        new BigDecimal(price),
        new BigDecimal("1.87"),
        new BigDecimal("0.81"),
        1843200L);
  }
}
