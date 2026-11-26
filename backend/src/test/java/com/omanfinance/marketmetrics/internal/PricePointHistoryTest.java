package com.omanfinance.marketmetrics.internal;

import static com.omanfinance.marketmetrics.internal.MarketMetricsConstants.CANDLE_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.omanfinance.candlebuffer.api.CandleBuffer;
import com.omanfinance.candlebuffer.api.dto.Candle;
import com.omanfinance.marketmetrics.api.dto.PricePoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricePointHistoryTest {

  private CandleBuffer candleBuffer;
  private PricePointHistory history;

  @BeforeEach
  void setUp() {
    candleBuffer = mock(CandleBuffer.class);
    history = new PricePointHistory(candleBuffer);
  }

  @Test
  void getPricePoints_maps_five_candles_to_close_prices() {
    when(candleBuffer.getLastCandles("AAPL", CANDLE_WINDOW)).thenReturn(fiveCandles());

    List<PricePoint> points = history.getPricePoints("AAPL");

    assertThat(points).hasSize(5);

    assertThat(points.get(0).timestamp()).isEqualTo(Instant.parse("2026-10-29T16:00:00Z"));
    assertThat(points.get(0).price()).isEqualByComparingTo("101");

    assertThat(points.get(1).timestamp()).isEqualTo(Instant.parse("2026-10-29T16:01:00Z"));
    assertThat(points.get(1).price()).isEqualByComparingTo("102");

    assertThat(points.get(2).timestamp()).isEqualTo(Instant.parse("2026-10-29T16:02:00Z"));
    assertThat(points.get(2).price()).isEqualByComparingTo("103");

    assertThat(points.get(3).timestamp()).isEqualTo(Instant.parse("2026-10-29T16:03:00Z"));
    assertThat(points.get(3).price()).isEqualByComparingTo("104");

    assertThat(points.get(4).timestamp()).isEqualTo(Instant.parse("2026-10-29T16:04:00Z"));
    assertThat(points.get(4).price()).isEqualByComparingTo("110");
  }

  @Test
  void getPricePoints_returns_null_when_no_candles() {
    when(candleBuffer.getLastCandles("AAPL", CANDLE_WINDOW)).thenReturn(List.of());

    assertThat(history.getPricePoints("AAPL")).isNull();
  }

  private static List<Candle> fiveCandles() {
    return List.of(
        candle("2026-10-29T16:00:00Z", "100", "101", 10),
        candle("2026-10-29T16:01:00Z", "101", "102", 20),
        candle("2026-10-29T16:02:00Z", "102", "103", 30),
        candle("2026-10-29T16:03:00Z", "103", "104", 40),
        candle("2026-10-29T16:04:00Z", "104", "110", 50));
  }

  private static Candle candle(String timestamp, String open, String close, long volume) {
    return new Candle(
        "AAPL",
        Instant.parse(timestamp),
        new BigDecimal(open),
        new BigDecimal(close),
        new BigDecimal(open),
        new BigDecimal(close),
        volume);
  }
}
