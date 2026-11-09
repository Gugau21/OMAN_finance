package com.omanfinance.candlebuffer.internal;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
class CandleBufferStartup {
  private final MarketIngestion marketIngestion;
  private final CandleBackfiller candleBackfiller;
  private final AtomicBoolean ready = new AtomicBoolean(false);
  private static final int NUMBER_OF_CANDLES_PER_SYMBOL_AT_STARTUP = 60;

  CandleBufferStartup(MarketIngestion marketIngestion, CandleBackfiller candleBackfiller) {
    this.marketIngestion = marketIngestion;
    this.candleBackfiller = candleBackfiller;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(1)
  void backfillCandles() {
    for (Stock stock : marketIngestion.getTrackedStocks()) {
      candleBackfiller.fetchAndSaveCandles(stock.symbol(), NUMBER_OF_CANDLES_PER_SYMBOL_AT_STARTUP);
    }
    ready.set(true);
  }

  public boolean isReady() {
    return ready.get();
  }
}
