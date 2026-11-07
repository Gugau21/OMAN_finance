package com.omanfinance.marketingestion.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

/**
 * Raw Alpaca bars in Redis. Each symbol has two keys:
 *
 * <ul>
 *   <li>{@code bars:{symbol}}: a sorted set of bars as JSON, scored by their timestamp in epoch
 *       milliseconds;
 *   <li>{@code bars:{symbol}:span}: a hash with {@code from} and {@code to} in epoch milliseconds,
 *       the time range whose bars were all fetched from Alpaca.
 * </ul>
 *
 * <p>The span is needed because Alpaca leaves out minutes with no trades: from the bars alone, a
 * missing minute and a minute that was never fetched look the same.
 */
@Component
@Profile("!fake")
class BarRedisRepository {

  static final String KEY_PREFIX = "bars:";
  static final String SPAN_SUFFIX = ":span";
  private static final String SPAN_FROM = "from";
  private static final String SPAN_TO = "to";

  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  BarRedisRepository(StringRedisTemplate redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
  }

  /**
   * Saves {@code bars} as everything Alpaca has for {@code symbol} in {@code [from, to]}, in one
   * transaction:
   *
   * <ul>
   *   <li>the bars already stored in {@code [from, to]} are replaced, so a bar fetched again does
   *       not sit next to its old copy, and a minute Alpaca no longer returns disappears;
   *   <li>the span grows to include {@code [from, to]} when the two overlap or touch, and is
   *       replaced by {@code [from, to]} when they do not, because the gap between them was never
   *       fetched.
   * </ul>
   *
   * <p>An empty {@code bars} still counts as fetched: Alpaca had no trades in that range.
   *
   * <p>Two saves of the same symbol at the same time can lose part of the span, so a later request
   * fetches that part again; neither can make the span cover a range that was not fetched.
   *
   * @throws IllegalArgumentException when {@code from} is after {@code to} or a bar's timestamp is
   *     outside {@code [from, to]}
   */
  void save(String symbol, List<Bar> bars, Instant from, Instant to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from " + from + " is after to " + to);
    }
    Set<TypedTuple<String>> members =
        bars.stream()
            .map(bar -> toMember(bar, from, to))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Span fetched = new Span(from, to);
    Span span =
        readSpan(symbol).filter(fetched::overlapsOrTouches).map(fetched::union).orElse(fetched);
    String key = keyOf(symbol);
    String spanKey = spanKeyOf(symbol);
    redis.execute(
        new SessionCallback<List<Object>>() {
          @Override
          @SuppressWarnings("unchecked")
          public <K, V> List<Object> execute(RedisOperations<K, V> operations)
              throws DataAccessException {
            RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
            ops.multi();
            ops.opsForZSet().removeRangeByScore(key, scoreOf(from), scoreOf(to));
            // ZADD with no members is a Redis error, so an empty fetch only clears the range.
            if (!members.isEmpty()) {
              ops.opsForZSet().add(key, members);
            }
            ops.opsForHash()
                .putAll(
                    spanKey,
                    Map.of(
                        SPAN_FROM, String.valueOf(span.from().toEpochMilli()),
                        SPAN_TO, String.valueOf(span.to().toEpochMilli())));
            return ops.exec();
          }
        });
  }

  /**
   * Whether every bar of {@code symbol} in {@code [from, to]} was fetched and is stored, so {@link
   * #read} returns all of them.
   */
  boolean covers(String symbol, Instant from, Instant to) {
    return readSpan(symbol).map(span -> span.contains(from, to)).orElse(false);
  }

  /** The bars stored for {@code symbol} in {@code [from, to]}, both ends included, oldest first. */
  List<Bar> read(String symbol, Instant from, Instant to) {
    Set<String> members =
        redis.opsForZSet().rangeByScore(keyOf(symbol), scoreOf(from), scoreOf(to));
    if (members == null) {
      return List.of();
    }
    return members.stream().map(this::fromJson).toList();
  }

  static String keyOf(String symbol) {
    return KEY_PREFIX + symbol;
  }

  static String spanKeyOf(String symbol) {
    return KEY_PREFIX + symbol + SPAN_SUFFIX;
  }

  private Optional<Span> readSpan(String symbol) {
    Map<Object, Object> fields = redis.opsForHash().entries(spanKeyOf(symbol));
    Object from = fields.get(SPAN_FROM);
    Object to = fields.get(SPAN_TO);
    if (from == null || to == null) {
      return Optional.empty();
    }
    return Optional.of(
        new Span(
            Instant.ofEpochMilli(Long.parseLong((String) from)),
            Instant.ofEpochMilli(Long.parseLong((String) to))));
  }

  private TypedTuple<String> toMember(Bar bar, Instant from, Instant to) {
    Instant t = bar.timestamp();
    if (t.isBefore(from) || t.isAfter(to)) {
      throw new IllegalArgumentException(
          "bar at " + t + " is outside the saved range [" + from + ", " + to + "]");
    }
    return TypedTuple.of(toJson(bar), scoreOf(t));
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

  private Bar fromJson(String member) {
    try {
      return json.readValue(member, Bar.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot read cached bar: " + member, e);
    }
  }

  /** A time range with both ends included. */
  private record Span(Instant from, Instant to) {

    boolean overlapsOrTouches(Span other) {
      return !from.isAfter(other.to) && !other.from.isAfter(to);
    }

    Span union(Span other) {
      return new Span(
          from.isBefore(other.from) ? from : other.from, to.isAfter(other.to) ? to : other.to);
    }

    boolean contains(Instant start, Instant end) {
      return !start.isBefore(from) && !end.isAfter(to);
    }
  }
}
