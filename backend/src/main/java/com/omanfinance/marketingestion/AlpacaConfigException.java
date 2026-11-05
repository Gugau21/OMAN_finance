package com.omanfinance.marketingestion;

/**
 * Alpaca rejected the API keys with 401 or 403. Trying again will not help until the keys in {@code
 * ALPACA_API_KEY_ID} and {@code ALPACA_API_SECRET_KEY} are fixed. It is not an {@link
 * AlpacaUnavailableException}, so code that retries on that one does not retry on this.
 */
public class AlpacaConfigException extends RuntimeException {

  public AlpacaConfigException(String message) {
    super(message);
  }

  public AlpacaConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
