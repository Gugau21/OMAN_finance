package com.omanfinance.marketingestion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Query for Alpaca {@code GET /v2/stocks/bars}. The client adds {@code feed=iex} itself.
 *
 * <p>{@code symbols} and {@code timeframe} are required. A null {@code limit}, {@code sort}, {@code
 * adjustment} or {@code currency} gets the same default Alpaca uses. A null {@code start}, {@code
 * end} or {@code asof} is left out of the query, so Alpaca uses the start of the current day, now
 * and today.
 */
public record BarsRequest(
    List<String> symbols,
    String timeframe,
    Instant start,
    Instant end,
    Integer limit,
    Sort sort,
    Adjustment adjustment,
    LocalDate asof,
    String currency,
    String pageToken) {

  /** The only timeframe this iteration uses. */
  public static final String ONE_MINUTE = "1Min";

  public static final int DEFAULT_LIMIT = 1000;
  public static final int MAX_LIMIT = 10_000;
  public static final String DEFAULT_CURRENCY = "USD";

  public BarsRequest {
    if (symbols == null || symbols.isEmpty()) {
      throw new IllegalArgumentException("symbols must not be empty");
    }
    if (symbols.stream().anyMatch(s -> s == null || s.isBlank())) {
      throw new IllegalArgumentException("symbols must not contain blanks: " + symbols);
    }
    symbols = List.copyOf(symbols);
    if (timeframe == null || timeframe.isBlank()) {
      throw new IllegalArgumentException("timeframe is required");
    }
    if (start != null && end != null && start.isAfter(end)) {
      throw new IllegalArgumentException("start " + start + " is after end " + end);
    }
    limit = limit == null ? DEFAULT_LIMIT : limit;
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be 1-" + MAX_LIMIT + ", was " + limit);
    }
    sort = sort == null ? Sort.ASC : sort;
    adjustment = adjustment == null ? Adjustment.RAW : adjustment;
    currency = currency == null ? DEFAULT_CURRENCY : currency;
  }

  /** A request for {@code symbols} in {@code [start, end]}, everything else at its default. */
  public static BarsRequest of(List<String> symbols, String timeframe, Instant start, Instant end) {
    return new BarsRequest(symbols, timeframe, start, end, null, null, null, null, null, null);
  }

  /** Returns a copy that asks for the page after the one that returned {@code token}. */
  BarsRequest withPageToken(String token) {
    return new BarsRequest(
        symbols, timeframe, start, end, limit, sort, adjustment, asof, currency, token);
  }

  /** Order of bars by timestamp. */
  public enum Sort {
    ASC("asc"),
    DESC("desc");

    private final String value;

    Sort(String value) {
      this.value = value;
    }

    /** The value Alpaca expects in the query string. */
    public String value() {
      return value;
    }
  }

  /** Corporate-action adjustment applied to prices. */
  public enum Adjustment {
    RAW("raw"),
    SPLIT("split"),
    DIVIDEND("dividend"),
    SPIN_OFF("spin-off"),
    ALL("all");

    private final String value;

    Adjustment(String value) {
      this.value = value;
    }

    /** The value Alpaca expects in the query string. */
    public String value() {
      return value;
    }
  }
}
