package com.omanfinance.clientfeed;

/**
 * The symbol in the request path is not one of the tracked stocks. {@link ApiExceptionHandler}
 * answers it with 404.
 */
public class StockNotFoundException extends RuntimeException {

  public StockNotFoundException(String symbol) {
    super("Stock is not tracked: " + symbol);
  }
}
