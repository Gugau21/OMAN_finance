package com.omanfinance.shared.web;

import java.util.Arrays;

/**
 * Reads {@code app.cors.allowed-origins}, the browser origins allowed to call the REST API and to
 * open the STOMP WebSocket.
 */
public final class AllowedOrigins {

  /** The property, with its default, for {@code @Value}. */
  public static final String PROPERTY = "${app.cors.allowed-origins:http://localhost}";

  private AllowedOrigins() {}

  /** Splits a comma-separated list of origins, trimming each one and dropping empty ones. */
  public static String[] parse(String commaSeparated) {
    return Arrays.stream(commaSeparated.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .toArray(String[]::new);
  }
}
