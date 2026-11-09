package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AlpacaRestClientConfigTest {

  private final AtomicReference<String> receivedPath = new AtomicReference<>();
  private final AtomicReference<Headers> receivedHeaders = new AtomicReference<>();
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private HttpServer server;
  private volatile Duration responseDelay = Duration.ZERO;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(executor);
    server.createContext("/", this::answer);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
    executor.shutdownNow();
  }

  @Test
  void sendsTheApiKeysToTheMarketDataApi() {
    RestClient client = clientWithReadTimeout(Duration.ofSeconds(5));

    String body = client.get().uri("/v2/stocks/bars").retrieve().body(String.class);

    assertThat(body).isEqualTo("{}");
    assertThat(receivedPath.get()).isEqualTo("/v2/stocks/bars");
    assertThat(receivedHeaders.get().getFirst(AlpacaRestClientConfig.API_KEY_ID_HEADER))
        .isEqualTo("key-id");
    assertThat(receivedHeaders.get().getFirst(AlpacaRestClientConfig.API_SECRET_KEY_HEADER))
        .isEqualTo("secret-key");
  }

  @Test
  void givesUpWhenAlpacaAnswersAfterTheReadTimeout() {
    responseDelay = Duration.ofSeconds(2);
    RestClient client = clientWithReadTimeout(Duration.ofMillis(200));

    assertThatThrownBy(() -> client.get().uri("/v2/stocks/bars").retrieve().body(String.class))
        .isInstanceOf(AlpacaUnavailableException.class);
  }

  private RestClient clientWithReadTimeout(Duration readTimeout) {
    URI baseUrl = URI.create("http://localhost:" + server.getAddress().getPort());
    AlpacaProperties properties =
        new AlpacaProperties(
            "key-id",
            "secret-key",
            URI.create("http://trading.invalid"),
            baseUrl,
            URI.create("ws://stream.invalid"),
            Duration.ofSeconds(1),
            readTimeout,
            Duration.ofDays(7));
    return new AlpacaRestClientConfig().alpacaDataRestClient(RestClient.builder(), properties);
  }

  private void answer(HttpExchange exchange) throws IOException {
    receivedPath.set(exchange.getRequestURI().getPath());
    receivedHeaders.set(exchange.getRequestHeaders());
    try {
      Thread.sleep(responseDelay.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    try (var out = exchange.getResponseBody()) {
      out.write(body);
    }
  }
}
