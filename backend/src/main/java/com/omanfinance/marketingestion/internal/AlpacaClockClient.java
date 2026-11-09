package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.dto.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads the market status from Alpaca {@code GET /v2/clock} on the trading API. */
@Component
@Profile("!fake")
class AlpacaClockClient {

  static final String CLOCK_PATH = "/v2/clock";

  private final RestClient restClient;

  AlpacaClockClient(@Qualifier("alpacaTradingRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * The current market status, values exactly as Alpaca sends them.
   *
   * @throws com.omanfinance.marketingestion.api.AlpacaUnavailableException when Alpaca returns 5xx,
   *     does not answer in time, or still returns 429 after one retry
   * @throws com.omanfinance.marketingestion.api.AlpacaConfigException when Alpaca rejects the API
   *     keys with 401 or 403
   */
  Clock fetch() {
    return restClient.get().uri(CLOCK_PATH).retrieve().body(Clock.class);
  }
}
