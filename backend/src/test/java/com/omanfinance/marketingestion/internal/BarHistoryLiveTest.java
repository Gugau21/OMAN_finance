package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

/**
 * Real Alpaca and real Redis together. Runs only when {@code ALPACA_API_KEY_ID} is set:
 *
 * <pre>
 * set -a; source ../.env; set +a
 * ./gradlew test --tests '*BarHistoryLiveTest' -i
 * </pre>
 *
 * <p>Uses SPY and QQQ, which the backend does not track, so a running backend's cache is not
 * touched. Their keys are deleted before and after each test.
 */
@EnabledIfEnvironmentVariable(named = "ALPACA_API_KEY_ID", matches = ".+")
@DataRedisTest
@Import({BarRedisRepository.class, TestClock.Config.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@EnableConfigurationProperties(AlpacaProperties.class)
class BarHistoryLiveTest {

  private static final List<String> SYMBOLS = List.of("SPY", "QQQ");

  /** The first five minutes of the 2026-11-05 session, 09:30-09:35 ET. */
  private static final Instant START = Instant.parse("2026-11-05T14:30:00Z");

  private static final Instant END = Instant.parse("2026-11-05T14:35:00Z");

  @Autowired private BarRedisRepository cache;
  @Autowired private StringRedisTemplate redis;
  @Autowired private TestClock clock;

  private AlpacaBarsClient alpaca;
  private BarHistory client;

  @BeforeEach
  void setUp() {
    deleteTestKeys();
    clock.set(Instant.now());
    AlpacaProperties properties =
        new AlpacaProperties(
            System.getenv("ALPACA_API_KEY_ID"),
            System.getenv("ALPACA_API_SECRET_KEY"),
            URI.create("https://paper-api.alpaca.markets"),
            URI.create(
                Objects.requireNonNullElse(
                    System.getenv("ALPACA_DATA_BASE_URL"), "https://data.alpaca.markets")),
            URI.create("wss://stream.data.alpaca.markets/v2/iex"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofDays(7),
            Duration.ofMinutes(15),
            Duration.ofSeconds(1),
            Duration.ofSeconds(60));
    RestClient restClient =
        new AlpacaRestClientConfig().alpacaDataRestClient(RestClient.builder(), properties);
    alpaca = spy(new AlpacaBarsClient(restClient));
    client = new BarHistory(alpaca, cache, clock);
  }

  @AfterEach
  void deleteTestKeys() {
    redis.delete(SYMBOLS.stream().map(BarRedisRepository::keyOf).toList());
  }

  @Test
  void answersTheSecondRequestFromRedisWithTheSameBarsAlpacaSent() {
    BarsRequest request = BarsRequest.of(SYMBOLS, BarsRequest.ONE_MINUTE, START, END);

    BarsResponse fromAlpaca = client.getBars(request);
    BarsResponse fromRedis = client.getBars(request);

    print("from Alpaca", fromAlpaca);
    print("from Redis", fromRedis);
    verify(alpaca, times(1)).fetchUpToLimit(any());
    assertThat(fromAlpaca.bars()).containsOnlyKeys(SYMBOLS);
    assertThat(fromRedis).isEqualTo(fromAlpaca);
  }

  @Test
  void includesTheBarsAtStartAndEnd() {
    BarsResponse response =
        client.getBars(BarsRequest.of(List.of("SPY"), BarsRequest.ONE_MINUTE, START, END));

    // SPY trades every minute, so a missing first or last bar means Alpaca left that end out.
    assertThat(response.barsFor("SPY")).extracting(Bar::timestamp).contains(START, END);
  }

  @Test
  void stopsAtTheLimitWithOneRequestAndCachesOnlyTheMinutesItReached() {
    BarsRequest limited =
        new BarsRequest(
            List.of("SPY"), BarsRequest.ONE_MINUTE, START, END, 2, null, null, null, null, null);
    Instant secondMinute = START.plusSeconds(60);

    BarsResponse fromAlpaca = client.getBars(limited);
    BarsResponse fromRedis =
        client.getBars(BarsRequest.of(List.of("SPY"), BarsRequest.ONE_MINUTE, START, secondMinute));

    verify(alpaca, times(1)).fetchUpToLimit(any());
    verify(alpaca, times(1)).fetchPage(any());
    // SPY trades every minute, so the two bars are the first two minutes.
    assertThat(fromAlpaca.barsFor("SPY"))
        .extracting(Bar::timestamp)
        .containsExactly(START, secondMinute);
    assertThat(fromRedis).isEqualTo(fromAlpaca);
  }

  private static void print(String label, BarsResponse response) {
    System.out.println(label + ":");
    response.bars().forEach((symbol, bars) -> bars.forEach(bar -> System.out.println("  " + bar)));
  }
}
