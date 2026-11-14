package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.dto.Bar;
import java.util.List;

/**
 * One message from the Alpaca market data WebSocket. Alpaca sends them in JSON arrays, one or more
 * per frame; {@link AlpacaStreamParser} turns a frame into these.
 */
sealed interface AlpacaStreamMessage {

  /** {@code {"T":"success","msg":"connected"}}: sent once the WebSocket opens, before auth. */
  record Connected() implements AlpacaStreamMessage {}

  /** {@code {"T":"success","msg":"authenticated"}}: the API keys were accepted. */
  record Authenticated() implements AlpacaStreamMessage {}

  /**
   * {@code {"T":"subscription",...}}: the full list of symbols now subscribed to minute bars, sent
   * after every {@code subscribe} or {@code unsubscribe}.
   */
  record Subscribed(List<String> bars) implements AlpacaStreamMessage {}

  /** {@code {"T":"b",...}}: one minute bar, values exactly as Alpaca sent them. */
  record BarMessage(Bar bar) implements AlpacaStreamMessage {}

  /**
   * {@code {"T":"error","code":...,"msg":...}}. Alpaca closes the connection after most of them,
   * for example 402 (auth failed) and 406 (connection limit exceeded).
   */
  record ErrorMessage(int code, String msg) implements AlpacaStreamMessage {}
}
