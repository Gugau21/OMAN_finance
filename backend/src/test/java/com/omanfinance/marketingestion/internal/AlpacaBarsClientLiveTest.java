package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Calls the real Alpaca market data API. Runs only when {@code ALPACA_API_KEY_ID} is set, so CI
 * skips it. Run it locally with the keys from {@code .env}:
 *
 * <pre>
 * set -a; source ../.env; set +a
 * ./gradlew test --tests '*AlpacaBarsClientLiveTest' -i
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "ALPACA_API_KEY_ID", matches = ".+")
class AlpacaBarsClientLiveTest {

  /** The first five minutes of the 2026-11-05 session, 09:30-09:35 ET. */
  private static final Instant START = Instant.parse("2026-11-05T14:30:00Z");

  private static final Instant END = Instant.parse("2026-11-05T14:35:00Z");

  private AlpacaBarsClient client;

  @BeforeEach
  void setUp() {
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
            Duration.ofDays(7));
    client =
        new AlpacaBarsClient(
            new AlpacaRestClientConfig().alpacaDataRestClient(RestClient.builder(), properties));
  }

  @Test
  void fetchesRealMinuteBarsForTwoSymbols() {
    BarsResponse response =
        client.fetchPage(
            BarsRequest.of(List.of("AAPL", "MSFT"), BarsRequest.ONE_MINUTE, START, END));

    response.bars().forEach((symbol, bars) -> bars.forEach(System.out::println));
    System.out.println("next_page_token=" + response.nextPageToken());

    assertThat(response.bars()).containsOnlyKeys("AAPL", "MSFT");
    for (String symbol : List.of("AAPL", "MSFT")) {
      assertThat(response.barsFor(symbol))
          .isNotEmpty()
          .allSatisfy(bar -> assertLooksReal(bar, symbol));
    }
  }

  @Test
  void returnsANextPageTokenWhenTheLimitCutsThePageShort() {
    BarsRequest request =
        new BarsRequest(
            List.of("AAPL", "MSFT"),
            BarsRequest.ONE_MINUTE,
            START,
            END,
            2,
            null,
            null,
            null,
            null,
            null);

    BarsResponse first = client.fetchPage(request);
    BarsResponse second = client.fetchPage(request.withPageToken(first.nextPageToken()));

    System.out.println("page 1: " + first.bars() + " next_page_token=" + first.nextPageToken());
    System.out.println("page 2: " + second.bars() + " next_page_token=" + second.nextPageToken());

    assertThat(first.hasNextPage()).isTrue();
    assertThat(second.bars()).isNotEmpty();
    assertThat(second.bars()).isNotEqualTo(first.bars());
  }

  private static void assertLooksReal(Bar bar, String symbol) {
    assertThat(bar.symbol()).isEqualTo(symbol);
    assertThat(bar.timestamp()).isBetween(START, END);
    assertThat(bar.open()).isPositive();
    assertThat(bar.high()).isGreaterThanOrEqualTo(bar.low());
    assertThat(bar.close()).isPositive();
    assertThat(bar.vwap()).isGreaterThan(BigDecimal.ZERO);
    assertThat(bar.volume()).isPositive();
  }
}
