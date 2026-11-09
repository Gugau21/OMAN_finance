package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.omanfinance.marketingestion.api.AlpacaConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
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

  private String get() {
    return client.get().uri(PATH).retrieve().body(String.class);
  }
}
