package com.omanfinance.clientfeed;

/**
 * The market data an endpoint needs is not loaded yet, for example while the backend is still
 * filling its buffers at startup. {@link ApiExceptionHandler} answers it with 503.
 */
public class MarketDataNotReadyException extends RuntimeException {

  public MarketDataNotReadyException(String message) {
    super(message);
  }
}
