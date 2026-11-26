package com.omanfinance.marketmetrics.internal;

import static com.omanfinance.marketmetrics.internal.MarketMetricsConstants.CANDLE_WINDOW;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketmetrics.api.dto.StockStat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class StockStatCalculator {
  private static final int CHANGE_PERCENT_SCALE = 10;
  private static final int PERCENT = 100;

  private final CandleBuffer candleBuffer;

  public StockStatCalculator(CandleBuffer candleBuffer) {
    this.candleBuffer = candleBuffer;
  }

  /** Returns {@code null} when there is no data (e.g. backfill failed for this symbol). */
  public StockStat getStockStat(String symbol) {
    List<Candle> candles = candleBuffer.getLastCandles(symbol, CANDLE_WINDOW);
    if (candles.isEmpty()) {
      return null;
    }

    return calculateStockStat(symbol, candles);
  }

  private StockStat calculateStockStat(String symbol, List<Candle> candles) {
    Instant timestamp = candles.getLast().timestamp();

    BigDecimal price = candles.getLast().close();
    BigDecimal prevPrice = candles.getFirst().open();

    BigDecimal change = price.subtract(prevPrice);
    BigDecimal changePercent =
        change
            .divide(prevPrice, CHANGE_PERCENT_SCALE, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(PERCENT));
    long volume = candles.stream().mapToLong(Candle::volume).sum();

    return new StockStat(symbol, timestamp, price, change, changePercent, volume);
  }
}
