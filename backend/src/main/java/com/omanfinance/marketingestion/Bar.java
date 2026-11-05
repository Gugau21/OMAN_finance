package com.omanfinance.marketingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One raw minute bar exactly as Alpaca sends it. Live WebSocket bars carry the symbol in {@code S};
 * REST bars do not, so the symbol is set from the key of the {@code bars} map with {@link
 * #withSymbol(String)}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Bar(
    @JsonProperty("S") String symbol,
    @JsonProperty("t") Instant timestamp,
    @JsonProperty("o") BigDecimal open,
    @JsonProperty("h") BigDecimal high,
    @JsonProperty("l") BigDecimal low,
    @JsonProperty("c") BigDecimal close,
    @JsonProperty("v") long volume,
    @JsonProperty("n") long tradeCount,
    @JsonProperty("vw") BigDecimal vwap) {

  /** Returns a copy of this bar for the given symbol, values unchanged. */
  public Bar withSymbol(String symbol) {
    return new Bar(symbol, timestamp, open, high, low, close, volume, tradeCount, vwap);
  }
}
