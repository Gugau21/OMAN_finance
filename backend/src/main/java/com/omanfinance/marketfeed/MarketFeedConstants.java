package com.omanfinance.marketfeed;

/** Values shared by REST, STOMP and live streams in marketfeed. */
public final class MarketFeedConstants {

  /** Last N trading minutes for candles, sparklines and stats ({@code api_contract.yaml}). */
  public static final int WINDOW_SIZE = 60;

  private MarketFeedConstants() {}
}
