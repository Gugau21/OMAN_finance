package com.omanfinance.marketfeed.web;

import static com.omanfinance.marketfeed.web.ApiContract.matchesContract;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.candlebuffer.api.dto.PricePoint;
import com.omanfinance.candlebuffer.api.dto.StockStat;
import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockDataController.class)
@Import(ApiExceptionHandler.class)
class StockDataControllerTest {

  private static final int WINDOW_SIZE = 60;
  private static final Instant BASE = Instant.parse("2026-10-29T16:00:00Z");

  @Autowired private MockMvc mvc;

  @MockitoBean private CandleBuffer candleBuffer;
  @MockitoBean private MarketIngestion marketIngestion;

  @BeforeEach
  void trackedStocks() {
    when(marketIngestion.getTrackedStocks())
        .thenReturn(
            List.of(new Stock("MSFT", "Microsoft Corporation"), new Stock("AAPL", "Apple Inc.")));
  }

  @Test
  void sparkline_returns_contract_shape_for_every_tracked_stock() throws Exception {
    when(candleBuffer.isReady()).thenReturn(true);
    when(candleBuffer.getPricePoints(eq("MSFT"), eq(WINDOW_SIZE))).thenReturn(sixtyPricePoints());
    when(candleBuffer.getPricePoints(eq("AAPL"), eq(WINDOW_SIZE))).thenReturn(sixtyPricePoints());

    mvc.perform(get("/api/stocks/sparkline"))
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].symbol").value("MSFT"))
        .andExpect(jsonPath("$[0].points.length()").value(60))
        .andExpect(jsonPath("$[1].symbol").value("AAPL"));
  }

  @Test
  void stat_returns_contract_shape_for_every_tracked_stock() throws Exception {
    when(candleBuffer.isReady()).thenReturn(true);
    when(candleBuffer.getStockStat(eq("MSFT"), eq(WINDOW_SIZE))).thenReturn(sampleStat("MSFT"));
    when(candleBuffer.getStockStat(eq("AAPL"), eq(WINDOW_SIZE))).thenReturn(sampleStat("AAPL"));

    mvc.perform(get("/api/stocks/stat"))
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].symbol").value("MSFT"))
        .andExpect(jsonPath("$[1].symbol").value("AAPL"));
  }

  @Test
  void candles_returns_contract_shape_for_tracked_symbol() throws Exception {
    when(candleBuffer.isReady()).thenReturn(true);
    when(candleBuffer.getLastCandles(eq("AAPL"), eq(WINDOW_SIZE))).thenReturn(sixtyCandles("AAPL"));

    mvc.perform(get("/api/stocks/AAPL/candles"))
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.symbol").value("AAPL"))
        .andExpect(jsonPath("$.candles.length()").value(60));
  }

  @Test
  void returns503_when_buffer_not_ready() throws Exception {
    when(candleBuffer.isReady()).thenReturn(false);

    mvc.perform(get("/api/stocks/sparkline")).andExpect(status().isServiceUnavailable());
    mvc.perform(get("/api/stocks/stat")).andExpect(status().isServiceUnavailable());
    mvc.perform(get("/api/stocks/AAPL/candles")).andExpect(status().isServiceUnavailable());
  }

  @Test
  void candles_returns404_for_unknown_symbol() throws Exception {
    when(candleBuffer.isReady()).thenReturn(true);

    mvc.perform(get("/api/stocks/XYZ/candles")).andExpect(status().isNotFound());
  }

  private static List<PricePoint> sixtyPricePoints() {
    List<PricePoint> points = new ArrayList<>();
    for (int i = 0; i < WINDOW_SIZE; i++) {
      Instant t = BASE.plusSeconds(60L * i);
      points.add(new PricePoint(t, new BigDecimal("100").add(new BigDecimal(i))));
    }
    return points;
  }

  private static List<Candle> sixtyCandles(String symbol) {
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < WINDOW_SIZE; i++) {
      Instant t = BASE.plusSeconds(60L * i);
      BigDecimal price = new BigDecimal("100").add(new BigDecimal(i));
      candles.add(new Candle(symbol, t, price, price, price, price, 1000L + i));
    }
    return candles;
  }

  private static StockStat sampleStat(String symbol) {
    return new StockStat(
        symbol,
        BASE.plusSeconds(60L * (WINDOW_SIZE - 1)),
        new BigDecimal("159"),
        new BigDecimal("1"),
        new BigDecimal("0.63"),
        1_000_000L);
  }
}
