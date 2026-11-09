package com.omanfinance.marketingestion.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client for the Alpaca market data API. Not created under the {@code fake} profile. */
@Configuration
@Profile("!fake")
class AlpacaRestClientConfig {

  static final String API_KEY_ID_HEADER = "APCA-API-KEY-ID";
  static final String API_SECRET_KEY_HEADER = "APCA-API-SECRET-KEY";

  /**
   * A {@link RestClient} rooted at {@code alpaca.data-base-url}. It sends the API keys with every
   * request and gives up after {@code alpaca.connect-timeout} or {@code alpaca.read-timeout}.
   * Alpaca's error answers become the exceptions {@code MarketIngestion} documents (see {@link
   * AlpacaErrorHandling}).
   */
  @Bean
  RestClient alpacaDataRestClient(RestClient.Builder builder, AlpacaProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.connectTimeout());
    requestFactory.setReadTimeout(properties.readTimeout());

    return AlpacaErrorHandling.applyTo(builder)
        .requestFactory(requestFactory)
        .baseUrl(properties.dataBaseUrl().toString())
        .defaultHeader(API_KEY_ID_HEADER, properties.apiKeyId())
        .defaultHeader(API_SECRET_KEY_HEADER, properties.apiSecretKey())
        .build();
  }
}
