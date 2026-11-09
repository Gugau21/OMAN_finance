package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import com.omanfinance.marketingestion.api.dto.Clock;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Synthetic market data for the {@code fake} profile, so other modules can run without Alpaca keys.
 *
 * <p>The market is always open and there is a bar for every symbol in every minute. A bar depends
 * only on its symbol and minute, so a live bar is equal to the historical bar {@link #getBars}
 * returns for the same minute, and each bar opens at the previous bar's close.
 */
@Component
@Profile("fake")
public class FakeMarketIngestion implements MarketIngestion {

  /** The stocks the fake tracks. */
  public static final List<Stock> STOCKS =
      List.of(
          new Stock("AAPL", "Apple Inc."),
          new Stock("MSFT", "Microsoft Corporation"),
          new Stock("JPM", "JPMorgan Chase & Co."),
          new Stock("KO", "The Coca-Cola Company"),
          new Stock("NVDA", "NVIDIA Corporation"));

  /** The symbols of {@link #STOCKS}, in the same order. */
  public static final List<String> SYMBOLS = STOCKS.stream().map(Stock::symbol).toList();

  private static final Duration MINUTE = Duration.ofMinutes(1);

  // Shape of the synthetic prices: a base price per symbol, a slow wave over one trading day
  // (390 minutes), a fast wave, and noise, each as a fraction of the base price.
  private static final int MIN_BASE_PRICE = 50;
  private static final int BASE_PRICE_SPREAD = 450;
  private static final int PHASE_STEPS = 1000;
  private static final double SLOW_WAVE_AMPLITUDE = 0.03;
  private static final double SLOW_WAVE_MINUTES = 390;
  private static final double FAST_WAVE_AMPLITUDE = 0.01;
  private static final double FAST_WAVE_MINUTES = 37;
  private static final double NOISE_AMPLITUDE = 0.002;

  /** How far high and low reach past the open and close, at most, as a fraction. */
  private static final double MAX_WICK = 0.001;

  private static final int PRICE_SCALE = 2;
  private static final int VWAP_SCALE = 4;
  private static final int VWAP_TERMS = 3;
  private static final int MIN_VOLUME = 100;
  private static final int VOLUME_SPREAD = 5_000;
  private static final int SHARES_PER_TRADE = 50;

  private final InstantSource time;
  private final Flux<Bar> liveBars;

  /** Uses the system clock and Reactor's parallel scheduler. */
  public FakeMarketIngestion() {
    this(InstantSource.system(), Schedulers.parallel());
  }

  /** For tests: {@code time} and {@code scheduler} should agree, e.g. both from virtual time. */
  FakeMarketIngestion(InstantSource time, Scheduler scheduler) {
    this.time = time;
    this.liveBars =
        Flux.defer(
                () -> {
                  Instant now = time.instant();
                  Instant currentMinute = now.truncatedTo(ChronoUnit.MINUTES);
                  Duration untilClose = Duration.between(now, currentMinute.plus(MINUTE));
                  return Flux.interval(untilClose, MINUTE, scheduler)
                      .map(tick -> currentMinute.plus(MINUTE.multipliedBy(tick)));
                })
            .flatMapIterable(minute -> SYMBOLS.stream().map(s -> bar(s, minute)).toList())
            .share();
  }

  /**
   * Emits one bar per tracked symbol when each minute ends, for the minute that just ended. The
   * stream is shared and does not complete.
   */
  @Override
  public Flux<Bar> streamBars() {
    return liveBars;
  }

  /**
   * Bars for every requested symbol, tracked or not, for each completed minute in {@code [start,
   * end]}. A null {@code start} means the start of the current UTC day and a null {@code end} means
   * now. {@code limit}, {@code adjustment}, {@code asof}, {@code currency} and {@code pageToken}
   * are ignored.
   *
   * @throws IllegalArgumentException if the timeframe is not {@link BarsRequest#ONE_MINUTE}
   */
  @Override
  public BarsResponse getBars(BarsRequest request) {
    if (!BarsRequest.ONE_MINUTE.equals(request.timeframe())) {
      throw new IllegalArgumentException(
          "the fake only supports " + BarsRequest.ONE_MINUTE + ", was " + request.timeframe());
    }
    Instant now = time.instant();
    Instant lastCompleted = now.truncatedTo(ChronoUnit.MINUTES).minus(MINUTE);
    Instant from =
        request.start() == null ? now.truncatedTo(ChronoUnit.DAYS) : ceilToMinute(request.start());
    Instant to =
        request.end() == null || request.end().isAfter(lastCompleted)
            ? lastCompleted
            : request.end();

    Map<String, List<Bar>> bars = new LinkedHashMap<>();
    for (String symbol : request.symbols()) {
      List<Bar> list = new ArrayList<>();
      for (Instant minute = from; !minute.isAfter(to); minute = minute.plus(MINUTE)) {
        list.add(bar(symbol, minute));
      }
      if (request.sort() == BarsRequest.Sort.DESC) {
        Collections.reverse(list);
      }
      if (!list.isEmpty()) {
        bars.put(symbol, list);
      }
    }
    return new BarsResponse(bars, null);
  }

  /** An open market whose {@code next_close} is the end of the current UTC day. */
  @Override
  public Clock getMarketStatus() {
    return currentClock();
  }

  /** Emits the current status once to each subscriber and then nothing; it does not complete. */
  @Override
  public Flux<Clock> streamMarketStatus() {
    return Mono.fromSupplier(this::currentClock).concatWith(Flux.never());
  }

  @Override
  public List<Stock> getTrackedStocks() {
    return STOCKS;
  }

  @Override
  public boolean addSymbol(String symbol) {
    throw new UnsupportedOperationException("addSymbol is not implemented in this iteration");
  }

  @Override
  public boolean removeSymbol(String symbol) {
    throw new UnsupportedOperationException("removeSymbol is not implemented in this iteration");
  }

  private Clock currentClock() {
    Instant now = time.instant();
    Instant endOfDay = now.truncatedTo(ChronoUnit.DAYS).plus(Duration.ofDays(1));
    return new Clock(now, true, endOfDay, endOfDay);
  }

  private static Instant ceilToMinute(Instant instant) {
    Instant floor = instant.truncatedTo(ChronoUnit.MINUTES);
    return floor.equals(instant) ? floor : floor.plus(MINUTE);
  }

  private static Bar bar(String symbol, Instant minute) {
    long index = minute.getEpochSecond() / MINUTE.toSeconds();
    SplittableRandom random = new SplittableRandom(seed(symbol, index));
    BigDecimal open = price(symbol, index);
    BigDecimal close = price(symbol, index + 1);
    double top = open.max(close).doubleValue();
    double bottom = open.min(close).doubleValue();
    BigDecimal high = toPrice(top * (1 + MAX_WICK * random.nextDouble())).max(open.max(close));
    BigDecimal low = toPrice(bottom * (1 - MAX_WICK * random.nextDouble())).min(open.min(close));
    BigDecimal vwap =
        high.add(low)
            .add(close)
            .divide(BigDecimal.valueOf(VWAP_TERMS), VWAP_SCALE, RoundingMode.HALF_UP);
    long volume = MIN_VOLUME + random.nextInt(VOLUME_SPREAD);
    long tradeCount = 1 + volume / SHARES_PER_TRADE;
    return new Bar(symbol, minute, open, high, low, close, volume, tradeCount, vwap);
  }

  /** The price at the start of minute {@code index}: a slow and a fast wave plus a little noise. */
  private static BigDecimal price(String symbol, long index) {
    double base = MIN_BASE_PRICE + Math.floorMod(symbol.hashCode(), BASE_PRICE_SPREAD);
    double phase = Math.floorMod(symbol.hashCode(), PHASE_STEPS) * 2 * Math.PI / PHASE_STEPS;
    double noise = new SplittableRandom(~seed(symbol, index)).nextDouble(-1, 1);
    double factor =
        1
            + SLOW_WAVE_AMPLITUDE * Math.sin(2 * Math.PI * index / SLOW_WAVE_MINUTES + phase)
            + FAST_WAVE_AMPLITUDE * Math.sin(2 * Math.PI * index / FAST_WAVE_MINUTES + 2 * phase)
            + NOISE_AMPLITUDE * noise;
    return toPrice(base * factor);
  }

  private static long seed(String symbol, long index) {
    return (long) symbol.hashCode() << Integer.SIZE ^ index;
  }

  private static BigDecimal toPrice(double value) {
    return BigDecimal.valueOf(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
  }
}
