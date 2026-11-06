package com.omanfinance.marketingestion.internal;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alpaca connection settings. Keys may be blank so the backend can start under the {@code fake}
 * profile without them.
 *
 * <p>{@code connectTimeout} and {@code readTimeout} apply to every REST call to Alpaca. A call that
 * runs past either fails instead of blocking the caller.
 */
@ConfigurationProperties("alpaca")
public record AlpacaProperties(
    String apiKeyId,
    String apiSecretKey,
    URI tradingBaseUrl,
    URI dataBaseUrl,
    URI streamUrl,
    Duration connectTimeout,
    Duration readTimeout) {

  /** Keeps the secret key out of logs. */
  @Override
  public String toString() {
    return ("AlpacaProperties[apiKeyId=%s, apiSecretKey=***, tradingBaseUrl=%s, dataBaseUrl=%s,"
            + " streamUrl=%s, connectTimeout=%s, readTimeout=%s]")
        .formatted(apiKeyId, tradingBaseUrl, dataBaseUrl, streamUrl, connectTimeout, readTimeout);
  }
}
