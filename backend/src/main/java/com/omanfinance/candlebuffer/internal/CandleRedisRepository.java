package com.omanfinance.candlebuffer.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.candlebuffer.api.dto.Candle;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
class CandleRedisRepository {

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public CandleRedisRepository(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  void saveCandles(String symbol, List<Candle> candles) {
    String key = "candles:" + symbol;

    redis.execute(
        new SessionCallback<Object>() {
          @Override
          @SuppressWarnings({"unchecked", "null"})
          public <K, V> Object execute(@NonNull RedisOperations<K, V> ops) {
            RedisOperations<String, String> redisOps = (RedisOperations<String, String>) ops;
            redisOps.multi();
            for (var candle : candles) {
              String member = toJson(candle);
              double score = candle.timestamp().getEpochSecond();
              redisOps.opsForZSet().add(key, member, score);
            }
            redisOps.opsForZSet().removeRange(key, 0, -61); // ZREMRANGEBYRANK 0 -61
            return redisOps.exec();
          }
        });
  }

  void saveCandle(String symbol, Candle candle) {
    saveCandles(symbol, List.of(candle));
  }

  List<Candle> lastCandles(String symbol, int n) {
    ZSetOperations<String, String> set = redis.opsForZSet();
    String key = "candles:" + symbol;

    Set<String> members = set.range(key, -n, -1);
    if (members == null || members.isEmpty()) {
      return List.of();
    }
    return members.stream().map(this::fromJson).toList();
  }

  private String toJson(Candle candle) {
    try {
      return objectMapper.writeValueAsString(candle);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Candle fromJson(String json) {
    try {
      return objectMapper.readValue(json, Candle.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
