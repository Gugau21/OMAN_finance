package com.omanfinance.candlebuffer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.candlebuffer.api.dto.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Runs against the real Redis that {@code spring.data.redis.*} points at. Only touches {@link
 * #KEY}, so it does not disturb candles stored by a running backend.
 */
@DataRedisTest
@Import(CandleRedisRepository.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class CandleRedisRepositoryTest {

  private static final String SYMBOL = "TEST_CANDLES";
  private static final String KEY = "candles:" + SYMBOL;

  @Autowired private CandleRedisRepository repository;
  @Autowired private StringRedisTemplate redis;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    deleteTestKey();
  }

  @AfterEach
  void deleteTestKey() {
    redis.delete(KEY);
  }

  @Test
  void saveCandle_stores_one_member_with_score_equal_to_open_time_epoch_seconds()
      throws JsonProcessingException {
    Instant openTime = Instant.parse("2026-11-06T15:30:00Z");
    Candle candle = candle(SYMBOL, openTime, "1", "2", "0.5", "1.5", 100L);

    repository.saveCandle(SYMBOL, candle);

    List<Candle> last = repository.getLastCandles(SYMBOL, 1);
    assertThat(last).hasSize(1);
    assertThat(last.getFirst()).isEqualTo(candle);
    assertThat(redis.opsForZSet().score(KEY, objectMapper.writeValueAsString(candle)))
        .isEqualTo((double) openTime.getEpochSecond());
  }

  @Test
  void saveCandle_sixty_one_distinct_minutes_keeps_newest_sixty() {
    Instant base = Instant.parse("2026-11-06T14:00:00Z");

    for (int i = 0; i <= 60; i++) {
      Instant t = base.plusSeconds(60L * i);
      repository.saveCandle(SYMBOL, candle(SYMBOL, t, "10", "11", "9", "10", 1000L + i));
    }

    List<Candle> last60 = repository.getLastCandles(SYMBOL, 60);
    assertThat(last60).hasSize(60);
    assertThat(last60.getFirst().timestamp()).isEqualTo(base.plusSeconds(60));
    assertThat(last60.getLast().timestamp()).isEqualTo(base.plusSeconds(60L * 60));
    assertThat(redis.opsForZSet().size(KEY)).isEqualTo(60L);
  }

  @Test
  void getLastCandles_returns_up_to_n_oldest_first() {
    Instant t0 = Instant.parse("2026-11-06T12:00:00Z");
    Instant t1 = Instant.parse("2026-11-06T12:01:00Z");
    Instant t2 = Instant.parse("2026-11-06T12:02:00Z");

    repository.saveCandle(SYMBOL, candle(SYMBOL, t0, "1", "1", "1", "1", 1L));
    repository.saveCandle(SYMBOL, candle(SYMBOL, t1, "2", "2", "2", "2", 2L));
    repository.saveCandle(SYMBOL, candle(SYMBOL, t2, "3", "3", "3", "3", 3L));

    List<Candle> two = repository.getLastCandles(SYMBOL, 2);

    assertThat(two).hasSize(2);
    assertThat(two.get(0).timestamp()).isEqualTo(t1);
    assertThat(two.get(1).timestamp()).isEqualTo(t2);
  }

  private static Candle candle(
      String symbol,
      Instant timestamp,
      String open,
      String high,
      String low,
      String close,
      long volume) {
    return new Candle(
        symbol,
        timestamp,
        new BigDecimal(open),
        new BigDecimal(high),
        new BigDecimal(low),
        new BigDecimal(close),
        volume);
  }
}
