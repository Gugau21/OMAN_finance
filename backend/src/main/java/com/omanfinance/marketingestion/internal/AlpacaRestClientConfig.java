package com.omanfinance.marketingestion.internal;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients for the Alpaca market data and trading APIs. Not created under the {@code fake}
 * profile.
 */
@Configuration
@Profile("!fake")
class AlpacaRestClientConfig {

  static final String API_KEY_ID_HEADER = "APCA-API-KEY-ID";
  static final String API_SECRET_KEY_HEADER = "APCA-API-SECRET-KEY";

  /** A {@link RestClient} rooted at {@code alpaca.data-base-url}, for historical bars. */
  @Bean
  RestClient alpacaDataRestClient(RestClient.Builder builder, AlpacaProperties properties) {
    return alpacaRestClient(builder, properties, properties.dataBaseUrl());
  }

  /** A {@link RestClient} rooted at {@code alpaca.trading-base-url}, for the market clock. */
  @Bean
  RestClient alpacaTradingRestClient(RestClient.Builder builder, AlpacaProperties properties) {
    return alpacaRestClient(builder, properties, properties.tradingBaseUrl());
  }

  /**
   * A {@link RestClient} rooted at {@code baseUrl}. It sends the API keys with every request and
   * gives up after {@code alpaca.connect-timeout} or {@code alpaca.read-timeout}. Alpaca's error
   * answers become the exceptions {@code MarketIngestion} documents, and a 429 is retried once
   * after at most {@code alpaca.max-retry-wait} (see {@link AlpacaErrorHandling}).
   */
  private static RestClient alpacaRestClient(
      RestClient.Builder builder, AlpacaProperties properties, URI baseUrl) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.connectTimeout());
    requestFactory.setReadTimeout(properties.readTimeout());

    return AlpacaErrorHandling.applyTo(builder, properties.maxRetryWait(), Thread::sleep)
        .requestFactory(requestFactory)
        .baseUrl(baseUrl.toString())
        .defaultHeader(API_KEY_ID_HEADER, properties.apiKeyId())
        .defaultHeader(API_SECRET_KEY_HEADER, properties.apiSecretKey())
        .build();
  }
}
