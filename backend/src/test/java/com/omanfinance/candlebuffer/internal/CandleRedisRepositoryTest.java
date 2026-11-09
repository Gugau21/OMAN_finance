package com.omanfinance.candlebuffer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omanfinance.candlebuffer.api.dto.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class CandleRedisRepositoryTest {

  private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

  @Container
  @SuppressWarnings("resource")
  private static final org.testcontainers.containers.GenericContainer<?> REDIS =
      new org.testcontainers.containers.GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate template;
  private ObjectMapper objectMapper;
  private CandleRedisRepository repository;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    template = new StringRedisTemplate(connectionFactory);
    template.afterPropertiesSet();

    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    repository = new CandleRedisRepository(template, objectMapper);
    template.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @AfterEach
  void tearDown() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void saveCandle_stores_one_member_with_score_equal_to_open_time_epoch_seconds()
      throws JsonProcessingException {
    Instant openTime = Instant.parse("2026-11-06T15:30:00Z");
    Candle candle = candle("AAPL", openTime, "1", "2", "0.5", "1.5", 100L);

    repository.saveCandle("AAPL", candle);

    List<Candle> last = repository.getLastCandles("AAPL", 1);
    assertThat(last).hasSize(1);
    assertThat(last.getFirst()).isEqualTo(candle);
    assertThat(template.opsForZSet().score("candles:AAPL", objectMapper.writeValueAsString(candle)))
        .isEqualTo((double) openTime.getEpochSecond());
  }

  @Test
  void saveCandle_sixty_one_distinct_minutes_keeps_newest_sixty() {
    Instant base = Instant.parse("2026-11-06T14:00:00Z");
    String symbol = "MSFT";

    for (int i = 0; i <= 60; i++) {
      Instant t = base.plusSeconds(60L * i);
      repository.saveCandle(symbol, candle(symbol, t, "10", "11", "9", "10", 1000L + i));
    }

    List<Candle> last60 = repository.getLastCandles(symbol, 60);
    assertThat(last60).hasSize(60);
    assertThat(last60.getFirst().timestamp()).isEqualTo(base.plusSeconds(60));
    assertThat(last60.getLast().timestamp()).isEqualTo(base.plusSeconds(60L * 60));
    assertThat(template.opsForZSet().size("candles:" + symbol)).isEqualTo(60L);
  }

  @Test
  void getLastCandles_returns_up_to_n_oldest_first() {
    Instant t0 = Instant.parse("2026-11-06T12:00:00Z");
    Instant t1 = Instant.parse("2026-11-06T12:01:00Z");
    Instant t2 = Instant.parse("2026-11-06T12:02:00Z");
    String symbol = "GOOG";

    repository.saveCandle(symbol, candle(symbol, t0, "1", "1", "1", "1", 1L));
    repository.saveCandle(symbol, candle(symbol, t1, "2", "2", "2", "2", 2L));
    repository.saveCandle(symbol, candle(symbol, t2, "3", "3", "3", "3", 3L));

    List<Candle> two = repository.getLastCandles(symbol, 2);

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
