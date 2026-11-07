package com.omanfinance.marketingestion.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
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
 * Raw Alpaca bars in Redis. Each symbol has one sorted set, {@code bars:{symbol}}, whose members
 * are bars as JSON scored by their timestamp in epoch milliseconds.
 */
@Component
@Profile("!fake")
class BarRedisRepository {

  static final String KEY_PREFIX = "bars:";

  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  BarRedisRepository(StringRedisTemplate redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
  }

  /**
   * Replaces whatever is stored for {@code symbol} in {@code [from, to]} with {@code bars}, in one
   * transaction. Removing the range first means a bar fetched again replaces the old copy instead
   * of sitting next to it, and a minute Alpaca no longer returns disappears.
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
    String key = keyOf(symbol);
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
            return ops.exec();
          }
        });
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
}
