package com.omanfinance.candlebuffer.internal;

import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StockStatCalculator {
  private static final int CHANGE_PERCENT_SCALE = 10;
  private static final int PERCENT = 100;

  private CandleHistory candleHistory;

  public StockStatCalculator(CandleHistory candleHistory) {
    this.candleHistory = candleHistory;
  }

  /** Returns {@code null} when there is no data (e.g. backfill failed for this symbol). */
  public StockStat getStockStat(String symbol, int windowSize) {
    List<Candle> candles = candleHistory.getLastCandles(symbol, windowSize);
    if (candles.isEmpty()) {
      return null;
    }

    Instant timestamp = candles.getLast().timestamp();

    BigDecimal price = candles.getLast().close();
    BigDecimal prevPrice = candles.getFirst().open();

    BigDecimal change = price.subtract(prevPrice);
    BigDecimal changePercent =
        change
            .divide(prevPrice, CHANGE_PERCENT_SCALE, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(PERCENT));
    long volume = candles.stream().mapToLong(Candle::volume).sum();

    StockStat stockStat = new StockStat(symbol, timestamp, price, change, changePercent, volume);

    return stockStat;
  }

  /** Returns {@code null} when there is no data (e.g. backfill failed for this symbol). */
  public List<PricePoint> getPricePoints(String symbol, int numberOfPricePoints) {
    List<Candle> candles = candleHistory.getLastCandles(symbol, numberOfPricePoints);
    if (candles.isEmpty()) {
      return null;
    }

    List<PricePoint> pricePoints = candles.stream().map(PricePoint::fromCandle).toList();

    return pricePoints;
  }
}
