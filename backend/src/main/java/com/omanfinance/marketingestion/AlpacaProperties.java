package com.omanfinance.marketingestion;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alpaca connection settings. Keys may be blank so the backend can start under the {@code fake}
 * profile without them.
 */
@ConfigurationProperties("alpaca")
public record AlpacaProperties(
    String apiKeyId, String apiSecretKey, URI tradingBaseUrl, URI dataBaseUrl, URI streamUrl) {

  /** Keeps the secret key out of logs. */
  @Override
  public String toString() {
    return "AlpacaProperties[apiKeyId=%s, apiSecretKey=***, tradingBaseUrl=%s, dataBaseUrl=%s, streamUrl=%s]"
        .formatted(apiKeyId, tradingBaseUrl, dataBaseUrl, streamUrl);
  }
}
