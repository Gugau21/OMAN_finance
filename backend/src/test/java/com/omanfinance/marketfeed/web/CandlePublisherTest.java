package com.omanfinance.marketfeed.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import reactor.core.publisher.Sinks;

class CandlePublisherTest {

  private static final Instant MINUTE = Instant.parse("2026-11-09T17:07:00Z");

  private static final Candle AAPL = candle("AAPL", "231.42");
  private static final Candle MSFT = candle("MSFT", "420.10");

  private final Sinks.Many<Candle> candles = Sinks.many().multicast().directBestEffort();
  private final SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);

  private CandlePublisher publisher;

  @BeforeEach
  void startPublisher() {
    CandleBuffer candleBuffer = mock(CandleBuffer.class);
    when(candleBuffer.streamCandles()).thenReturn(candles.asFlux());
    publisher = new CandlePublisher(candleBuffer, messaging);
    publisher.start();
  }

  @Test
  void publishesEveryCandleInOrderToItsTopic() {
    candles.tryEmitNext(AAPL);
    candles.tryEmitNext(MSFT);

    InOrder inOrder = inOrder(messaging);
    inOrder.verify(messaging).convertAndSend("/topic/stocks/AAPL/candles", AAPL);
    inOrder.verify(messaging).convertAndSend("/topic/stocks/MSFT/candles", MSFT);
  }

  @Test
  void keepsPublishingAfterAFailedSend() {
    doThrow(new MessageDeliveryException("broker down"))
        .when(messaging)
        .convertAndSend("/topic/stocks/AAPL/candles", AAPL);

    candles.tryEmitNext(AAPL);
    candles.tryEmitNext(MSFT);

    verify(messaging).convertAndSend("/topic/stocks/MSFT/candles", MSFT);
  }

  @Test
  void stopsPublishingOnShutdown() {
    publisher.stop();

    candles.tryEmitNext(AAPL);

    verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
  }

  private static Candle candle(String symbol, String close) {
    BigDecimal price = new BigDecimal(close);
    return new Candle(symbol, MINUTE, price, price, price, price, 31250L);
  }
}
