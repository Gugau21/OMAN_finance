package com.omanfinance.marketingestion.api.dto;

/**
 * A tracked stock: its ticker and company name, as {@code Stock} in {@code api_contract.yaml}.
 *
 * @param symbol the ticker, e.g. {@code AAPL}, at most 16 characters
 * @param name the company name, e.g. {@code Apple Inc.}
 */
public record Stock(String symbol, String name) {

  public static final int MAX_SYMBOL_LENGTH = 16;

  public Stock {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol is required");
    }
    if (symbol.length() > MAX_SYMBOL_LENGTH) {
      throw new IllegalArgumentException(
          "symbol must be at most " + MAX_SYMBOL_LENGTH + " characters, was " + symbol);
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required for " + symbol);
    }
  }
}
