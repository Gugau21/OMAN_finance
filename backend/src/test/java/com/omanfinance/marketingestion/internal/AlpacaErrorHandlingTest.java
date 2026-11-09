package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.omanfinance.marketingestion.api.AlpacaConfigException;
import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class AlpacaErrorHandlingTest {

  private static final String BASE_URL = "https://data.alpaca.markets";
  private static final String PATH = "/v2/stocks/bars";

  private MockRestServiceServer server;
  private RestClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder =
        AlpacaErrorHandling.applyTo(RestClient.builder().baseUrl(BASE_URL));
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
