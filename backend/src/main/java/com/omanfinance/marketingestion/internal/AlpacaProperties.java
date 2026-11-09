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
 *
 * <p>{@code maxRetryWait} is the longest wait before retrying a call that Alpaca answered with 429,
 * whatever its {@code Retry-After} header says.
 *
 * <p>{@code barCacheRetention} is how long bars fetched from Alpaca stay cached in Redis.
 *
 * <p>{@code clockRefreshInterval} is how often the market status is fetched again from {@code
 * /v2/clock}, on top of the calls at each open and close.
 */
@ConfigurationProperties("alpaca")
public record AlpacaProperties(
    String apiKeyId,
    String apiSecretKey,
    URI tradingBaseUrl,
    URI dataBaseUrl,
    URI streamUrl,
    Duration connectTimeout,
    Duration readTimeout,
    Duration maxRetryWait,
    Duration barCacheRetention,
    Duration clockRefreshInterval) {

  /** Keeps the secret key out of logs. */
  @Override
  public String toString() {
    return ("AlpacaProperties[apiKeyId=%s, apiSecretKey=***, tradingBaseUrl=%s, dataBaseUrl=%s,"
            + " streamUrl=%s, connectTimeout=%s, readTimeout=%s, maxRetryWait=%s, barCacheRetention=%s,"
            + " clockRefreshInterval=%s]")
        .formatted(
            apiKeyId,
            tradingBaseUrl,
            dataBaseUrl,
            streamUrl,
            connectTimeout,
            readTimeout,
            maxRetryWait,
            barCacheRetention,
            clockRefreshInterval);
  }
}
