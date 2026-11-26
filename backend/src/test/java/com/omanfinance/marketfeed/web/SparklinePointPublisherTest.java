package com.omanfinance.marketfeed.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.marketfeed.web.dto.ContractPricePoint;
import com.omanfinance.marketmetrics.api.MarketMetrics;
import com.omanfinance.marketmetrics.api.dto.PricePoint;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import reactor.core.publisher.Sinks;

class SparklinePointPublisherTest {

  private static final Instant MINUTE = Instant.parse("2026-11-09T17:07:00Z");

  private static final PricePoint AAPL = point("AAPL", "231.42");
  private static final PricePoint MSFT = point("MSFT", "420.10");

  private final Sinks.Many<PricePoint> points = Sinks.many().multicast().directBestEffort();
  private final SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);

  private SparklinePointPublisher publisher;

  @BeforeEach
  void startPublisher() {
    MarketMetrics marketMetrics = mock(MarketMetrics.class);
    when(marketMetrics.streamPricePoints()).thenReturn(points.asFlux());
    publisher = new SparklinePointPublisher(marketMetrics, messaging);
    publisher.start();
  }

  @Test
  void publishesEveryPointInOrderToTheSharedTopic() {
    points.tryEmitNext(AAPL);
    points.tryEmitNext(MSFT);

    InOrder inOrder = inOrder(messaging);
    inOrder
        .verify(messaging)
        .convertAndSend("/topic/stocks/sparkline", ContractPricePoint.from(AAPL));
    inOrder
        .verify(messaging)
        .convertAndSend("/topic/stocks/sparkline", ContractPricePoint.from(MSFT));
  }

  @Test
  void keepsPublishingAfterAFailedSend() {
    doThrow(new MessageDeliveryException("broker down"))
        .when(messaging)
        .convertAndSend("/topic/stocks/sparkline", ContractPricePoint.from(AAPL));

    points.tryEmitNext(AAPL);
    points.tryEmitNext(MSFT);

    verify(messaging).convertAndSend("/topic/stocks/sparkline", ContractPricePoint.from(MSFT));
  }

  @Test
  void stopsPublishingOnShutdown() {
    publisher.stop();

    points.tryEmitNext(AAPL);

    verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
  }

  private static PricePoint point(String symbol, String price) {
    return new PricePoint(symbol, MINUTE, new BigDecimal(price));
  }
}
