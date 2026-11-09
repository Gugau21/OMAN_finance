package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads historical bars from Alpaca {@code GET /v2/stocks/bars}. */
@Component
@Profile("!fake")
class AlpacaBarsClient {

  static final String BARS_PATH = "/v2/stocks/bars";

  /** The free Alpaca plan only serves the IEX feed, so every request asks for it. */
  static final String FEED = "iex";

  private final RestClient restClient;

  AlpacaBarsClient(@Qualifier("alpacaDataRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * Fetches the one page of bars that {@code request} describes, values exactly as Alpaca sends
   * them. Does not follow {@code next_page_token}.
   */
  BarsResponse fetchPage(BarsRequest request) {
    Map<String, Object> query = queryOf(request);
    return restClient
        .get()
        .uri(
            uri -> {
              uri.path(BARS_PATH);
              // Values go in as URI variables so they are fully encoded: a page token is base64
              // and its '+' would otherwise reach Alpaca as a space.
              query.keySet().forEach(name -> uri.queryParam(name, "{" + name + "}"));
              return uri.build(query);
            })
        .retrieve()
        .body(BarsResponse.class);
  }

  /**
   * Fetches every page of bars that {@code request} describes, following {@code next_page_token}
   * until Alpaca has no more pages, and returns them merged in one response with a null {@code
   * nextPageToken}.
   *
   * <p>Alpaca's {@code limit} counts bars across all symbols, so one symbol's bars can be split
   * across two pages; they are appended in the order the pages arrive.
   */
  BarsResponse fetchAll(BarsRequest request) {
    Map<String, List<Bar>> merged = new LinkedHashMap<>();
    BarsResponse page = fetchPage(request);
    appendTo(merged, page);
    while (page.hasNextPage()) {
      page = fetchPage(request.withPageToken(page.nextPageToken()));
      appendTo(merged, page);
    }
    return new BarsResponse(merged, null);
  }

  private static void appendTo(Map<String, List<Bar>> merged, BarsResponse page) {
    page.bars()
        .forEach(
            (symbol, bars) -> merged.computeIfAbsent(symbol, s -> new ArrayList<>()).addAll(bars));
  }

  /** The query parameters for {@code request}, leaving out the optional ones that are null. */
  private static Map<String, Object> queryOf(BarsRequest request) {
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("symbols", String.join(",", request.symbols()));
    query.put("timeframe", request.timeframe());
    query.put("start", request.start());
    query.put("end", request.end());
    query.put("limit", request.limit());
    query.put("adjustment", request.adjustment().value());
    query.put("asof", request.asof());
    query.put("feed", FEED);
    query.put("currency", request.currency());
    query.put("page_token", request.pageToken());
    query.put("sort", request.sort().value());
    // Alpaca falls back to its own default when start, end, asof or page_token is left out.
    query.values().removeIf(Objects::isNull);
    return query;
  }
}
