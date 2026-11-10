package com.omanfinance.marketfeed.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.omanfinance.TestRedisDatabase;
import com.omanfinance.marketfeed.MarketDataNotReadyException;
import com.omanfinance.marketfeed.StockNotFoundException;
import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import com.omanfinance.marketingestion.internal.StubAlpacaClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs on a real server port: MockMvc does not forward {@code sendError} to {@code /error}, so it
 * would not show the error body.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = TestRedisDatabase.PROPERTY)
@ActiveProfiles("fake")
@Import({StubAlpacaClock.Config.class, ApiExceptionHandlerTest.ThrowingController.class})
class ApiExceptionHandlerTest {

  @Autowired private TestRestTemplate rest;

  @AfterAll
  static void deleteWhatStartupSaved(@Autowired StringRedisTemplate redis) {
    TestRedisDatabase.flush(redis);
  }

  @Test
  void answersNotReadyWith503AndTheDefaultErrorBody() {
    ResponseEntity<JsonNode> response = rest.getForEntity("/test/not-ready", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertDefaultErrorBody(response.getBody(), 503, "Service Unavailable", "/test/not-ready");
  }

  @Test
  void answersAlpacaUnavailableWith503() {
    ResponseEntity<JsonNode> response =
        rest.getForEntity("/test/alpaca-unavailable", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertDefaultErrorBody(
        response.getBody(), 503, "Service Unavailable", "/test/alpaca-unavailable");
  }

  @Test
  void answersUnknownSymbolWith404AndTheDefaultErrorBody() {
    ResponseEntity<JsonNode> response = rest.getForEntity("/test/stocks/XYZ", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertDefaultErrorBody(response.getBody(), 404, "Not Found", "/test/stocks/XYZ");
  }

  private static void assertDefaultErrorBody(JsonNode body, int status, String error, String path) {
    assertThat(body).isNotNull();
    assertThat(body.path("timestamp").isTextual()).isTrue();
    assertThat(body.path("status").asInt()).isEqualTo(status);
    assertThat(body.path("error").asText()).isEqualTo(error);
    assertThat(body.path("path").asText()).isEqualTo(path);
  }

  /** Nested in a test class, so component scanning skips it; only this test imports it. */
  @RestController
  static class ThrowingController {

    @GetMapping("/test/not-ready")
    String notReady() {
      throw new MarketDataNotReadyException("candles not loaded yet");
    }

    @GetMapping("/test/alpaca-unavailable")
    String alpacaUnavailable() {
      throw new AlpacaUnavailableException("Alpaca returned 503");
    }

    @GetMapping("/test/stocks/{symbol}")
    String stock(@PathVariable String symbol) {
      throw new StockNotFoundException(symbol);
    }
  }
}
