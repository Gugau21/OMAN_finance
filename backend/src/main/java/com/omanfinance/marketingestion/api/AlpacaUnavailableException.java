package com.omanfinance.marketingestion.api;

/**
 * Alpaca could not answer for now: it returned 429 or 5xx, or the request timed out. Trying again
 * later may succeed.
 */
public class AlpacaUnavailableException extends RuntimeException {

  public AlpacaUnavailableException(String message) {
    super(message);
  }

  public AlpacaUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
