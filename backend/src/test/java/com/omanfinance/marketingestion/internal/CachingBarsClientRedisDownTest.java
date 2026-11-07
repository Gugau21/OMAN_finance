package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** {@code getBars} keeps working with bars from Alpaca when Redis cannot be reached. */
@ExtendWith(OutputCaptureExtension.class)
class CachingBarsClientRedisDownTest {

  private static final Instant T0 = Instant.parse("2026-11-05T14:30:00Z");
  private static final Instant T5 = T0.plusSeconds(300);
  private static final Clock CLOCK = Clock.fixed(T0.plusSeconds(3600), ZoneOffset.UTC);
  private static final BarsRequest REQUEST =
      BarsRequest.of(List.of("AAPL"), BarsRequest.ONE_MINUTE, T0, T5);
  private static final BarsResponse FROM_ALPACA =
      new BarsResponse(Map.of("AAPL", List.of(bar(T0))), null);

  private final AlpacaBarsClient alpaca = mock(AlpacaBarsClient.class);
  private final BarRedisRepository cache = mock(BarRedisRepository.class);
  private final CachingBarsClient client = new CachingBarsClient(alpaca, cache, CLOCK);

  @Test
  void getsTheBarsFromAlpacaWhenReadingTheCacheFails(CapturedOutput output) {
    when(cache.readIfComplete(anyString(), any(), any())).thenThrow(redisDown());
    when(alpaca.fetchAll(REQUEST)).thenReturn(FROM_ALPACA);

    assertThat(client.getBars(REQUEST)).isEqualTo(FROM_ALPACA);
    assertThat(output).contains("Bar cache unavailable, getting bars from Alpaca");
  }

  @Test
  void returnsTheBarsFromAlpacaWhenSavingToTheCacheFails(CapturedOutput output) {
    when(cache.readIfComplete(anyString(), any(), any())).thenReturn(Optional.empty());
    doThrow(redisDown()).when(cache).save(anyString(), anyList(), any(), any());
    when(alpaca.fetchAll(REQUEST)).thenReturn(FROM_ALPACA);

    assertThat(client.getBars(REQUEST)).isEqualTo(FROM_ALPACA);
    assertThat(output).contains("Bar cache unavailable, bars from Alpaca not cached");
  }

  /** No mocked cache: a real repository pointed at a port where no Redis listens. */
  @Test
  void worksWithARealRedisConnectionThatIsRefused(CapturedOutput output) {
    LettuceConnectionFactory factory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("localhost", 1),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).build());
    factory.afterPropertiesSet();
    factory.start();
    try {
      BarRedisRepository realCache =
          new BarRedisRepository(
              new StringRedisTemplate(factory),
              new ObjectMapper().findAndRegisterModules(),
              new AlpacaProperties(null, null, null, null, null, null, null, Duration.ofDays(7)),
              CLOCK);
      when(alpaca.fetchAll(REQUEST)).thenReturn(FROM_ALPACA);

      BarsResponse response = new CachingBarsClient(alpaca, realCache, CLOCK).getBars(REQUEST);

      assertThat(response).isEqualTo(FROM_ALPACA);
      assertThat(output)
          .contains("Bar cache unavailable, getting bars from Alpaca")
          .contains("Bar cache unavailable, bars from Alpaca not cached");
    } finally {
      factory.destroy();
    }
  }

  private static RedisConnectionFailureException redisDown() {
    return new RedisConnectionFailureException("Unable to connect to Redis");
  }

  private static Bar bar(Instant t) {
    BigDecimal price = BigDecimal.ONE;
    return new Bar("AAPL", t, price, price, price, price, 100, 1, price);
  }
}
