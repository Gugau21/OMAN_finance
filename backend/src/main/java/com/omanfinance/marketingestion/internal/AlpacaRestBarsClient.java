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
class AlpacaRestBarsClient {

  static final String BARS_PATH = "/v2/stocks/bars";

  /** The free Alpaca plan only serves the IEX feed, so every request asks for it. */
  static final String FEED = "iex";

  private final RestClient restClient;

  AlpacaRestBarsClient(@Qualifier("alpacaDataRestClient") RestClient restClient) {
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
   * Fetches the first {@code limit} bars that {@code request} describes, in Alpaca's order, and
   * returns them in one response with a null {@code nextPageToken}. Fewer when Alpaca has fewer.
   *
   * <p>Alpaca's {@code limit} is a page size: a page holds at most that many bars, counted across
   * all symbols, so one page is usually enough. Alpaca may still return a shorter page with a
   * {@code next_page_token}; then the next pages are fetched until there are {@code limit} bars or
   * no more pages, and bars past {@code limit} are dropped. A symbol's bars split across two pages
   * are appended in the order the pages arrive.
   */
  BarsResponse fetchUpToLimit(BarsRequest request) {
    Map<String, List<Bar>> merged = new LinkedHashMap<>();
    BarsResponse page = fetchPage(request);
    int missing = appendTo(merged, page, request.limit());
    while (missing > 0 && page.hasNextPage()) {
      page = fetchPage(request.withPageToken(page.nextPageToken()));
      missing = appendTo(merged, page, missing);
    }
    return new BarsResponse(merged, null);
  }

  /**
   * Appends at most {@code max} bars of {@code page} to {@code merged}, in page order.
   *
   * @return how many of the {@code max} bars are still missing
   */
  private static int appendTo(Map<String, List<Bar>> merged, BarsResponse page, int max) {
    int missing = max;
    for (Map.Entry<String, List<Bar>> entry : page.bars().entrySet()) {
      if (missing == 0) {
        break;
      }
      List<Bar> bars = entry.getValue();
      List<Bar> taken = bars.subList(0, Math.min(missing, bars.size()));
      merged.computeIfAbsent(entry.getKey(), s -> new ArrayList<>()).addAll(taken);
      missing -= taken.size();
    }
    return missing;
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
