package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.omanfinance.marketingestion.api.AlpacaConfigException;
import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class AlpacaRestErrorHandlingTest {

  private static final String BASE_URL = "https://data.alpaca.markets";
  private static final String PATH = "/v2/stocks/bars";
  private static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(30);

  private final List<Duration> waits = new ArrayList<>();
  private MockRestServiceServer server;
  private RestClient client;

  @BeforeEach
  void setUp() {
    useSleeper(waits::add);
  }

  /** Builds a new client whose waits before a retry go to {@code sleeper}. */
  private void useSleeper(AlpacaRestErrorHandling.Sleeper sleeper) {
    RestClient.Builder builder =
        AlpacaRestErrorHandling.applyTo(
            RestClient.builder().baseUrl(BASE_URL), MAX_RETRY_WAIT, sleeper);
    server = MockRestServiceServer.bindTo(builder).build();
    client = builder.build();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void throwsAlpacaConfigExceptionWhenAlpacaRejectsTheKeys(int status) {
    server.expect(requestTo(BASE_URL + PATH)).andRespond(withStatus(HttpStatus.valueOf(status)));

    assertThatThrownBy(this::get)
        .isInstanceOf(AlpacaConfigException.class)
        .hasMessageContaining(String.valueOf(status))
        .hasMessageContaining("ALPACA_API_KEY_ID");
  }

  @Test
  void leavesOtherClientErrorsAsTheyAre() {
    server.expect(requestTo(BASE_URL + PATH)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(this::get).isInstanceOf(HttpClientErrorException.BadRequest.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 502, 503})
  void throwsAlpacaUnavailableExceptionWhenAlpacaFails(int status) {
    server.expect(requestTo(BASE_URL + PATH)).andRespond(withStatus(HttpStatus.valueOf(status)));

    assertThatThrownBy(this::get)
        .isInstanceOf(AlpacaUnavailableException.class)
        .hasMessageContaining(String.valueOf(status));
  }

  @Test
  void throwsAlpacaUnavailableExceptionWhenAlpacaDoesNotAnswerInTime() {
    SocketTimeoutException timeout = new SocketTimeoutException("Read timed out");
    server.expect(requestTo(BASE_URL + PATH)).andRespond(withException(timeout));

    assertThatThrownBy(this::get).isInstanceOf(AlpacaUnavailableException.class).hasCause(timeout);
  }

  @Test
  void throwsAlpacaUnavailableExceptionWhenTheBodyStopsHalfway() {
    SocketTimeoutException timeout = new SocketTimeoutException("Read timed out");
    server
        .expect(requestTo(BASE_URL + PATH))
        .andRespond(
            request -> {
              MockClientHttpResponse response =
                  new MockClientHttpResponse(failingAfterFirstByte(timeout), HttpStatus.OK);
              response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
              return response;
            });

    assertThatThrownBy(this::get).isInstanceOf(AlpacaUnavailableException.class).hasCause(timeout);
  }

  @Test
  void returnsTheBodyOfASuccessfulAnswer() {
    server
        .expect(requestTo(BASE_URL + PATH))
        .andRespond(withSuccess("{\"bars\":{}}", MediaType.APPLICATION_JSON));

    assertThat(get()).isEqualTo("{\"bars\":{}}");
  }

  @Test
  void retriesOnceAfterRetryAfterWhenAlpacaAnswers429() {
    server.expect(requestTo(BASE_URL + PATH)).andRespond(rateLimited("3"));
    server
        .expect(requestTo(BASE_URL + PATH))
        .andRespond(withSuccess("{\"bars\":{}}", MediaType.APPLICATION_JSON));

    assertThat(get()).isEqualTo("{\"bars\":{}}");
    assertThat(waits).containsExactly(Duration.ofSeconds(3));
    server.verify();
  }

  @Test
  void throwsAlpacaUnavailableExceptionWhenAlpacaAnswers429AgainAfterTheRetry() {
    server.expect(twice(), requestTo(BASE_URL + PATH)).andRespond(rateLimited("2"));

    assertThatThrownBy(this::get)
        .isInstanceOf(AlpacaUnavailableException.class)
        .hasMessageContaining("429");
    assertThat(waits).containsExactly(Duration.ofSeconds(2));
    server.verify();
  }

  @Test
  void waitsAtMostTheMaxRetryWait() {
    server.expect(requestTo(BASE_URL + PATH)).andRespond(rateLimited("120"));
    server
        .expect(requestTo(BASE_URL + PATH))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    get();

    assertThat(waits).containsExactly(MAX_RETRY_WAIT);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"soon", "-5", "Wed, 11 Nov 2026 15:30:00 GMT"})
  void waitsTheDefaultWhenRetryAfterIsNotANumberOfSeconds(String retryAfter) {
    server.expect(requestTo(BASE_URL + PATH)).andRespond(rateLimited(retryAfter));
    server
        .expect(requestTo(BASE_URL + PATH))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    get();

    assertThat(waits).containsExactly(AlpacaRestErrorHandling.DEFAULT_RETRY_WAIT);
  }

  @Test
  void throwsAlpacaUnavailableExceptionWhenInterruptedWhileWaitingToRetry() {
    InterruptedException interruption = new InterruptedException();
    useSleeper(
        wait -> {
          throw interruption;
        });
    server.expect(requestTo(BASE_URL + PATH)).andRespond(rateLimited("1"));

    try {
      assertThatThrownBy(this::get)
          .isInstanceOf(AlpacaUnavailableException.class)
          .hasCause(interruption);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  /** A 429 answer with {@code retryAfter} as its {@code Retry-After} header, or none if null. */
  private static DefaultResponseCreator rateLimited(String retryAfter) {
    DefaultResponseCreator response = withStatus(HttpStatus.TOO_MANY_REQUESTS);
    return retryAfter == null ? response : response.header(HttpHeaders.RETRY_AFTER, retryAfter);
  }

  private static InputStream failingAfterFirstByte(IOException failure) {
    return new InputStream() {
      private boolean sentFirstByte;

      @Override
      public int read() throws IOException {
        if (sentFirstByte) {
          throw failure;
        }
        sentFirstByte = true;
        return '{';
      }
    };
  }

  private String get() {
    return client.get().uri(PATH).retrieve().body(String.class);
  }
}
