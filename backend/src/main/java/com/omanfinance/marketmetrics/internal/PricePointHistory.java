package com.omanfinance.marketmetrics.internal;

import static com.omanfinance.marketmetrics.internal.MarketMetricsConstants.CANDLE_WINDOW;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketmetrics.api.dto.PricePoint;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class PricePointHistory {

  private final CandleBuffer candleBuffer;

  PricePointHistory(CandleBuffer candleBuffer) {
    this.candleBuffer = candleBuffer;
  }

  /** Returns {@code null} when there is no data (e.g. backfill failed for this symbol). */
  List<PricePoint> getPricePoints(String symbol) {
    List<Candle> candles = candleBuffer.getLastCandles(symbol, CANDLE_WINDOW);
    if (candles.isEmpty()) {
      return null;
    }

    return candles.stream().map(PricePoint::fromCandle).toList();
  }
}
