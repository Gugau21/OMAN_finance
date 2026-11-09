package com.omanfinance.marketfeed.web;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import com.omanfinance.marketfeed.MarketDataNotReadyException;
import com.omanfinance.marketfeed.StockNotFoundException;
import com.omanfinance.marketfeed.web.dto.ContractCandle;
import com.omanfinance.marketfeed.web.dto.Sparkline;
import com.omanfinance.marketfeed.web.dto.StockCandles;
import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Candle, sparkline and stock-stat endpoints of {@code api_contract.yaml}. */
@RestController
@RequestMapping("/api/stocks")
public class StockDataController {

  private static final int WINDOW_SIZE = 60;

  private final CandleBuffer candleBuffer;
  private final MarketIngestion marketIngestion;

  public StockDataController(CandleBuffer candleBuffer, MarketIngestion marketIngestion) {
    this.candleBuffer = candleBuffer;
    this.marketIngestion = marketIngestion;
  }

  @GetMapping("/sparkline")
  public List<Sparkline> listSparklines() {
    requireReady();
    List<Sparkline> sparklines = new ArrayList<>();
    for (Stock stock : marketIngestion.getTrackedStocks()) {
      List<PricePoint> points = candleBuffer.getPricePoints(stock.symbol(), WINDOW_SIZE);
      requireDataForSymbol(stock.symbol(), points);
      sparklines.add(new Sparkline(stock.symbol(), points));
    }
    return sparklines;
  }

  @GetMapping("/stat")
  public List<StockStat> listStockStats() {
    requireReady();
    List<StockStat> stats = new ArrayList<>();
    for (Stock stock : marketIngestion.getTrackedStocks()) {
      StockStat stat = candleBuffer.getStockStat(stock.symbol(), WINDOW_SIZE);
      requireDataForSymbol(stock.symbol(), stat);
      stats.add(stat);
    }
    return stats;
  }

  @GetMapping("/{symbol}/candles")
  public StockCandles getStockCandles(@PathVariable String symbol) {
    requireReady();
    requireTracked(symbol);
    List<ContractCandle> candles =
        candleBuffer.getLastCandles(symbol, WINDOW_SIZE).stream()
            .map(ContractCandle::from)
            .toList();
    requireDataForSymbol(symbol, candles.isEmpty() ? null : candles);
    return new StockCandles(symbol, candles);
  }

  private void requireReady() {
    if (!candleBuffer.isReady()) {
      throw new MarketDataNotReadyException("candle buffer not loaded yet");
    }
  }

  private void requireTracked(String symbol) {
    boolean tracked =
        marketIngestion.getTrackedStocks().stream().anyMatch(s -> s.symbol().equals(symbol));
    if (!tracked) {
      throw new StockNotFoundException(symbol);
    }
  }

  private static void requireDataForSymbol(String symbol, Object data) {
    if (data == null) {
      throw new MarketDataNotReadyException("no candle data for " + symbol);
    }
    if (data instanceof List<?> list && list.isEmpty()) {
      throw new MarketDataNotReadyException("no candle data for " + symbol);
    }
  }
}
