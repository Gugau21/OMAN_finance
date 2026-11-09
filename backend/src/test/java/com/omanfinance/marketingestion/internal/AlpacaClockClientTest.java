package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.omanfinance.marketingestion.api.dto.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AlpacaClockClientTest {

  private static final String BASE_URL = "https://paper-api.alpaca.markets";

  private MockRestServiceServer server;
  private AlpacaClockClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new AlpacaClockClient(builder.build());
  }

  @Test
  void returnsTheRecordedClockFieldByField() {
    server
        .expect(requestTo(BASE_URL + AlpacaClockClient.CLOCK_PATH))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                new ClassPathResource("alpaca/clock-open.json"), MediaType.APPLICATION_JSON));

    Clock clock = client.fetch();

    assertThat(clock)
        .isEqualTo(
            new Clock(
                Instant.parse("2026-11-09T15:15:30.123456789Z"),
                true,
                Instant.parse("2026-11-10T14:30:00Z"),
                Instant.parse("2026-11-09T21:00:00Z")));
    server.verify();
  }
}
