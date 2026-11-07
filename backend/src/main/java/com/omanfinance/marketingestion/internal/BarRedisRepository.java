package com.omanfinance.marketingestion.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

/**
 * Raw Alpaca minute bars in Redis, one sorted set per symbol: {@code bars:{symbol}}, scored by
 * minute in epoch milliseconds.
 *
 * <p>Every fetched minute has exactly one entry:
 *
 * <ul>
 *   <li>the bar as JSON, when Alpaca sent one;
 *   <li>{@code none:{epoch ms}}, when Alpaca sent none because there were no trades.
 * </ul>
 *
 * <p>So a minute with no entry was never fetched, and a range is fully cached when it has as many
 * entries as minutes. Without the {@code none} entries, a minute with no trades and a minute never
 * fetched would look the same.
 *
 * <p>Entries older than {@code alpaca.bar-cache-retention} are deleted on every save of the same
 * symbol. A deleted minute counts as never fetched, so its range is fetched from Alpaca again.
 */
@Component
@Profile("!fake")
class BarRedisRepository {

  static final String KEY_PREFIX = "bars:";
  static final String NO_BAR_PREFIX = "none:";
  private static final Duration MINUTE = Duration.ofMinutes(1);

  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final Duration retention;
  private final Clock clock;

  BarRedisRepository(
      StringRedisTemplate redis, ObjectMapper json, AlpacaProperties properties, Clock clock) {
    this.redis = redis;
    this.json = json;
    this.retention = properties.barCacheRetention();
    this.clock = clock;
  }

  /**
   * Saves {@code bars} as everything Alpaca has for {@code symbol} in {@code [from, to]}: every
   * minute in the range gets its bar, or a {@code none} entry when {@code bars} has no bar for it.
   * Whatever was stored in the range before is replaced, in one transaction.
   *
   * <p>The same transaction deletes the entries of {@code symbol} older than {@code
   * alpaca.bar-cache-retention}, the new ones included, so a range older than that is never kept.
   *
   * <p>Save only minutes that are over: a minute saved before Alpaca has its bar is stored as a
   * minute with no trades.
   *
   * @throws IllegalArgumentException when {@code from} is after {@code to}, or a bar is outside
   *     {@code [from, to]} or not on a whole minute
   */
  void save(String symbol, List<Bar> bars, Instant from, Instant to) {
    checkRange(from, to);
    Set<TypedTuple<String>> entries = entriesFor(bars, from, to);
    String key = keyOf(symbol);
    Instant cutoff = clock.instant().minus(retention);

    inTransaction(
        ops -> {
          ops.opsForZSet().removeRangeByScore(key, scoreOf(from), scoreOf(to));
          // ZADD with no entries is a Redis error; a range with no whole minute has none.
          if (!entries.isEmpty()) {
            ops.opsForZSet().add(key, entries);
          }
          deleteOlderThan(ops, key, cutoff);
        });
  }

  /**
   * The bars of {@code symbol} in {@code [from, to]}, both ends included, oldest first, when every
   * minute in the range was fetched. Empty when any minute is missing, so the caller asks Alpaca.
   *
   * @throws IllegalArgumentException when {@code from} is after {@code to}
   */
  Optional<List<Bar>> readIfComplete(String symbol, Instant from, Instant to) {
    checkRange(from, to);
    Set<String> entries =
        redis.opsForZSet().rangeByScore(keyOf(symbol), scoreOf(from), scoreOf(to));
    if (entries == null || entries.size() != minutesIn(from, to).size()) {
      return Optional.empty();
    }
    return Optional.of(
        entries.stream().filter(entry -> !isNoBar(entry)).map(this::fromJson).toList());
  }

  static String keyOf(String symbol) {
    return KEY_PREFIX + symbol;
  }

  /** One entry per whole minute in {@code [from, to]}: its bar, or a {@code none} entry. */
  private Set<TypedTuple<String>> entriesFor(List<Bar> bars, Instant from, Instant to) {
    Set<Instant> minutesWithBar = new LinkedHashSet<>();
    Set<TypedTuple<String>> entries = new LinkedHashSet<>();
    for (Bar bar : bars) {
      Instant t = bar.timestamp();
      if (t.isBefore(from) || t.isAfter(to)) {
        throw new IllegalArgumentException(
            "bar at " + t + " is outside the saved range [" + from + ", " + to + "]");
      }
      if (!t.equals(t.truncatedTo(ChronoUnit.MINUTES))) {
        throw new IllegalArgumentException("bar at " + t + " is not on a whole minute");
      }
      minutesWithBar.add(t);
      entries.add(TypedTuple.of(toJson(bar), scoreOf(t)));
    }
    for (Instant minute : minutesIn(from, to)) {
      if (!minutesWithBar.contains(minute)) {
        entries.add(TypedTuple.of(NO_BAR_PREFIX + minute.toEpochMilli(), scoreOf(minute)));
      }
    }
    return entries;
  }

  /** Deletes the entries before {@code cutoff}; an entry exactly at {@code cutoff} stays. */
  private static void deleteOlderThan(
      RedisOperations<String, String> ops, String key, Instant cutoff) {
    // Scores are whole milliseconds, so up to cutoff - 1 ms leaves cutoff itself out.
    ops.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, scoreOf(cutoff) - 1);
  }

  /** The whole minutes in {@code [from, to]}, both ends included. */
  private static List<Instant> minutesIn(Instant from, Instant to) {
    Instant minute = from.truncatedTo(ChronoUnit.MINUTES);
    if (minute.isBefore(from)) {
      minute = minute.plus(MINUTE);
    }
    List<Instant> minutes = new ArrayList<>();
    while (!minute.isAfter(to)) {
      minutes.add(minute);
      minute = minute.plus(MINUTE);
    }
    return minutes;
  }

  /**
   * Runs {@code commands} in one {@code MULTI}/{@code EXEC}: Redis runs them together, with no
   * other client's command in between.
   */
  private void inTransaction(Consumer<RedisOperations<String, String>> commands) {
    redis.execute(
        new SessionCallback<List<Object>>() {
          @Override
          @SuppressWarnings("unchecked")
          public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
            RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
            ops.multi();
            commands.accept(ops);
            return ops.exec();
          }
        });
  }

  private static void checkRange(Instant from, Instant to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from " + from + " is after to " + to);
    }
  }

  private static boolean isNoBar(String entry) {
    return entry.startsWith(NO_BAR_PREFIX);
  }

  private static double scoreOf(Instant instant) {
    return instant.toEpochMilli();
  }

  private String toJson(Bar bar) {
    try {
      return json.writeValueAsString(bar);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot write bar as JSON: " + bar, e);
    }
  }

  private Bar fromJson(String entry) {
    try {
      return json.readValue(entry, Bar.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot read cached bar: " + entry, e);
    }
  }
}
