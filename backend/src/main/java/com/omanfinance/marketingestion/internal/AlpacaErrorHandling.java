package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.AlpacaConfigException;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Turns Alpaca's error answers into the exceptions that {@code MarketIngestion} documents, so its
 * callers never see Spring's HTTP client exceptions:
 *
 * <ul>
 *   <li>401 or 403: {@link AlpacaConfigException}, whose message names the environment variables
 *       that hold the API keys.
 * </ul>
 */
final class AlpacaErrorHandling {

  private AlpacaErrorHandling() {}

  /** Adds the error mapping to {@code builder}, so every client it builds has it. */
  static RestClient.Builder applyTo(RestClient.Builder builder) {
    return builder.defaultStatusHandler(
        AlpacaErrorHandling::isRejectedKeys, AlpacaErrorHandling::throwRejectedKeys);
  }

  private static boolean isRejectedKeys(HttpStatusCode status) {
    return status.isSameCodeAs(HttpStatus.UNAUTHORIZED)
        || status.isSameCodeAs(HttpStatus.FORBIDDEN);
  }

  private static void throwRejectedKeys(HttpRequest request, ClientHttpResponse response)
      throws IOException {
    throw new AlpacaConfigException(
        ("Alpaca rejected the API keys with %d on %s %s:"
                + " check ALPACA_API_KEY_ID and ALPACA_API_SECRET_KEY")
            .formatted(
                response.getStatusCode().value(), request.getMethod(), request.getURI().getPath()));
  }
}
