package com.omanfinance.marketingestion.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw body of Alpaca {@code GET /v2/stocks/bars}: bars per symbol plus the token for the next page.
 * Every bar gets its symbol from the map key, because REST bars do not carry one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BarsResponse(
    @JsonProperty("bars") Map<String, List<Bar>> bars,
    @JsonProperty("next_page_token") String nextPageToken) {

  public BarsResponse {
    Map<String, List<Bar>> copy = new LinkedHashMap<>();
    if (bars != null) {
      bars.forEach(
          (symbol, list) ->
              copy.put(
                  symbol,
                  list == null
                      ? List.of()
                      : list.stream().map(bar -> bar.withSymbol(symbol)).toList()));
    }
    bars = Collections.unmodifiableMap(copy);
  }

  /** The bars for {@code symbol}, or an empty list when Alpaca returned none. */
  public List<Bar> barsFor(String symbol) {
    return bars.getOrDefault(symbol, List.of());
  }

  /** Whether Alpaca has another page for the same request. */
  boolean hasNextPage() {
    return nextPageToken != null && !nextPageToken.isEmpty();
  }
}
