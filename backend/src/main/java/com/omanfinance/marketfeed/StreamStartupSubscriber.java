package com.omanfinance.marketfeed;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
class StreamStartupSubscriber {
  private final CandleBuffer candleBuffer;

  private final int START_AFTER_CANDLE_BUFFER_STARTUP_AND_CANDLE_PUBLISHER = 4;
  private final int WINDOW_FROM_WHICH_TO_COUNT_STATS = 60;

  public StreamStartupSubscriber(CandleBuffer candleBuffer) {
    this.candleBuffer = candleBuffer;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(START_AFTER_CANDLE_BUFFER_STARTUP_AND_CANDLE_PUBLISHER)
  void startStreams() {
    candleBuffer.ensureStreamStockStatStarted(WINDOW_FROM_WHICH_TO_COUNT_STATS);
  }
}
