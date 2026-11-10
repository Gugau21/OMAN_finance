package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsRequest.Adjustment;
import com.omanfinance.marketingestion.api.dto.BarsRequest.Sort;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Gets bars from the Redis bar cache when it holds every requested minute, otherwise from Alpaca,
 * and caches what Alpaca returns. Either way the caller gets the same bars Alpaca would send.
 *
 * <p>The cache only saves Alpaca calls: when Redis cannot be reached, bars come from Alpaca and a
 * warning is logged.
 */
@Component
@Profile("!fake")
class CachingBarsClient {

  /**
   * How long after a minute ends its bar may still change on Alpaca's side. A minute is cached only
   * once this has passed, otherwise it could be cached as a minute with no trades.
   */
  static final Duration SETTLE_TIME = Duration.ofMinutes(1);

  private static final Duration MINUTE = Duration.ofMinutes(1);
  private static final Logger log = LoggerFactory.getLogger(CachingBarsClient.class);

  private final AlpacaBarsClient alpaca;
  private final BarRedisRepository cache;
  private final Clock clock;

  CachingBarsClient(AlpacaBarsClient alpaca, BarRedisRepository cache, Clock clock) {
    this.alpaca = alpaca;
    this.cache = cache;
    this.clock = clock;
  }

  /**
   * The first {@code limit} bars for {@code request}, as {@link AlpacaBarsClient#fetchUpToLimit}.
   */
  BarsResponse getBars(BarsRequest request) {
    if (!isCacheable(request)) {
      return alpaca.fetchUpToLimit(request);
    }
    Optional<BarsResponse> cached = readFromCacheOrEmpty(request);
    if (cached.isPresent()) {
      return cached.get();
    }
    BarsResponse fetched = alpaca.fetchUpToLimit(request);
    saveToCacheIfPossible(request, fetched);
    return fetched;
  }

  /**
   * {@link #readFromCache}, or empty when Redis cannot be reached, so the bars come from Alpaca.
   */
  private Optional<BarsResponse> readFromCacheOrEmpty(BarsRequest request) {
    try {
      return readFromCache(request);
    } catch (DataAccessException e) {
      log.warn("Bar cache unavailable, getting bars from Alpaca: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /** {@link #saveToCache}, skipped with a warning when Redis cannot be reached. */
  private void saveToCacheIfPossible(BarsRequest request, BarsResponse fetched) {
    try {
      saveToCache(request, fetched);
    } catch (DataAccessException e) {
      log.warn("Bar cache unavailable, bars from Alpaca not cached: {}", e.getMessage());
    }
  }

  /**
   * Whether the cache can answer {@code request}. It holds raw USD minute bars on whole minutes, so
   * anything else goes straight to Alpaca:
   *
   * <ul>
   *   <li>another timeframe, adjustment, currency or {@code asof} would change the bars;
   *   <li>a missing {@code start} or {@code end} is filled in by Alpaca's own clock;
   *   <li>a {@code pageToken} asks for one page, but the cache answers with all of them.
   * </ul>
   */
  private static boolean isCacheable(BarsRequest request) {
    return request.timeframe().equals(BarsRequest.ONE_MINUTE)
        && request.adjustment() == Adjustment.RAW
        && request.currency().equals(BarsRequest.DEFAULT_CURRENCY)
        && request.asof() == null
        && request.pageToken() == null
        && isWholeMinute(request.start())
        && isWholeMinute(request.end());
  }

  /**
   * The cached bars for every symbol of {@code request}, or empty when any symbol misses a minute.
   * Like Alpaca, a symbol with no bars is left out.
   *
   * <p>A single symbol gets its first {@code limit} bars in {@code sort} order, as from Alpaca.
   * Several symbols with more than {@code limit} bars in total are left to Alpaca: which bars it
   * keeps then depends on how it orders the symbols.
   */
  private Optional<BarsResponse> readFromCache(BarsRequest request) {
    Map<String, List<Bar>> barsBySymbol = new LinkedHashMap<>();
    int total = 0;
    for (String symbol : request.symbols()) {
      Optional<List<Bar>> cached = cache.readIfComplete(symbol, request.start(), request.end());
      if (cached.isEmpty()) {
        return Optional.empty();
      }
      List<Bar> bars = cached.get();
      if (!bars.isEmpty()) {
        // The cache reads oldest first.
        List<Bar> sorted = request.sort() == Sort.DESC ? bars.reversed() : bars;
        barsBySymbol.put(symbol, sorted.subList(0, Math.min(request.limit(), sorted.size())));
        total += bars.size();
      }
    }
    if (total > request.limit() && request.symbols().size() > 1) {
      return Optional.empty();
    }
    return Optional.of(new BarsResponse(barsBySymbol, null));
  }

  /**
   * Caches the bars Alpaca returned for {@code request}, every symbol from {@code start} up to the
   * last settled minute. Minutes after it are left out, so a later request fetches them again.
   *
   * <p>When Alpaca returned {@code limit} bars it may have stopped early, so only the minutes those
   * bars reach are cached: for one symbol, up to its last bar in {@code sort} order; for several,
   * nothing, because which symbols are complete depends on how Alpaca orders them.
   */
  private void saveToCache(BarsRequest request, BarsResponse fetched) {
    Instant from = request.start();
    Instant to = min(request.end(), lastSettledMinute());
    if (totalBars(fetched) >= request.limit()) {
      if (request.symbols().size() > 1) {
        return;
      }
      Instant lastBar = fetched.barsFor(request.symbols().getFirst()).getLast().timestamp();
      if (request.sort() == Sort.ASC) {
        to = min(to, lastBar);
      } else {
        from = lastBar;
      }
    }
    if (!to.isBefore(from)) {
      saveRange(request, fetched, from, to);
    }
  }

  /** Caches the bars of every symbol of {@code request} in {@code [from, to]}. */
  private void saveRange(BarsRequest request, BarsResponse fetched, Instant from, Instant to) {
    for (String symbol : request.symbols()) {
      List<Bar> bars =
          fetched.barsFor(symbol).stream().filter(bar -> isInside(bar, from, to)).toList();
      cache.save(symbol, bars, from, to);
    }
  }

  /**
   * The last minute that ended at least {@link #SETTLE_TIME} ago. At 15:10:30 that is 15:08: 15:09
   * ended at 15:10:00, only 30 s ago.
   */
  private Instant lastSettledMinute() {
    Instant settledUntil = clock.instant().minus(SETTLE_TIME);
    return settledUntil.truncatedTo(ChronoUnit.MINUTES).minus(MINUTE);
  }

  private static int totalBars(BarsResponse response) {
    return response.bars().values().stream().mapToInt(List::size).sum();
  }

  private static boolean isWholeMinute(Instant time) {
    return time != null && time.equals(time.truncatedTo(ChronoUnit.MINUTES));
  }

  private static boolean isInside(Bar bar, Instant from, Instant to) {
    return !bar.timestamp().isBefore(from) && !bar.timestamp().isAfter(to);
  }

  private static Instant min(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }
}
