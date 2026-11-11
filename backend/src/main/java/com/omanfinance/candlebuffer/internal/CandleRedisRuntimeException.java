package com.omanfinance.candlebuffer.internal;

/**
 * Candle storage in Redis failed: the connection was down, the command failed, or a stored value
 * could not be (de)serialized. Callers that must not publish without a successful save (e.g. {@link
 * LiveCandleStream}) catch this and skip emitting on the live stream.
 */
public class CandleRedisRuntimeException extends RuntimeException {
  public CandleRedisRuntimeException(String message) {
    super(message);
  }

  public CandleRedisRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
