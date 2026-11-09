package com.omanfinance.candlebuffer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandleBufferStartupTest {

  @Test
  void backfillCandles_loads_every_symbol_then_marks_ready() {
    MarketIngestion marketIngestion = mock(MarketIngestion.class);
    CandleBackfiller candleBackfiller = mock(CandleBackfiller.class);
    when(marketIngestion.getTrackedStocks())
        .thenReturn(
            List.of(new Stock("AAPL", "Apple Inc."), new Stock("MSFT", "Microsoft Corporation")));
    CandleBufferStartup startup = new CandleBufferStartup(marketIngestion, candleBackfiller);

    assertThat(startup.isReady()).isFalse();

    startup.backfillCandles();

    verify(candleBackfiller).fetchAndSaveCandles("AAPL", 60);
    verify(candleBackfiller).fetchAndSaveCandles("MSFT", 60);
    assertThat(startup.isReady()).isTrue();
  }
}
