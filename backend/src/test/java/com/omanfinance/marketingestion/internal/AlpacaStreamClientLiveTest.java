package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Real Alpaca stream: prints each live bar as it arrives. Runs only when asked to, because it holds
 * the one stream connection Alpaca allows per API key for minutes:
 *
 * <pre>
 * export $(grep -E '^ALPACA_(API_KEY_ID|API_SECRET_KEY)=' ../.env | xargs)
 * ALPACA_STREAM_LIVE_TEST=true ./gradlew test --tests '*AlpacaStreamClientLiveTest' --rerun -i
 * </pre>
 *
 * <p>By default it uses Alpaca's test stream, whose symbol {@code FAKEPACA} gets bars at any time.
 * For real bars during market hours set {@code ALPACA_STREAM_URL} (e.g. {@code
 * wss://stream.data.alpaca.markets/v2/iex}) and {@code ALPACA_STREAM_SYMBOLS} (e.g. {@code
 * AAPL,MSFT}). {@code ALPACA_STREAM_BARS} is how many bars to wait for (default 3).
 */
@EnabledIfEnvironmentVariable(named = "ALPACA_STREAM_LIVE_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "ALPACA_API_KEY_ID", matches = ".+")
class AlpacaStreamClientLiveTest {

  @Test
  void printsLiveBarsAsTheyArrive() throws InterruptedException {
    URI url = URI.create(env("ALPACA_STREAM_URL", "wss://stream.data.alpaca.markets/v2/test"));
    List<String> symbols = List.of(env("ALPACA_STREAM_SYMBOLS", "FAKEPACA").split(","));
    int wanted = Integer.parseInt(env("ALPACA_STREAM_BARS", "3"));
    AlpacaProperties properties =
        new AlpacaProperties(
            System.getenv("ALPACA_API_KEY_ID"),
            System.getenv("ALPACA_API_SECRET_KEY"),
            null,
            null,
            url,
            Duration.ofSeconds(5),
            null,
            null,
            null,
            null,
            Duration.ofSeconds(1),
            Duration.ofSeconds(60));
    ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    LiveBarStream liveBars = new LiveBarStream();
    AlpacaStreamClient client =
        new AlpacaStreamClient(
            properties,
            new WebSocketAdapter(properties),
            new AlpacaStreamParser(json),
            liveBars,
            json);
    CountDownLatch bars = new CountDownLatch(wanted);
    liveBars
        .flux()
        .subscribe(
            bar -> {
              System.out.println("BAR " + bar);
              bars.countDown();
            });

    client.connect(symbols);
    try {
      // Bars come once a minute, shortly after each minute ends.
      assertThat(bars.await(wanted + 2L, TimeUnit.MINUTES)).isTrue();
    } finally {
      client.disconnect();
    }
  }

  private static String env(String name, String fallback) {
    return Objects.requireNonNullElse(System.getenv(name), fallback);
  }
}
