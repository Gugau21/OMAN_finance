package com.omanfinance.marketingestion.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Authenticated;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.BarMessage;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Connected;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.ErrorMessage;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Subscribed;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Turns one text frame from the Alpaca market data WebSocket into {@link AlpacaStreamMessage}s, in
 * the order Alpaca sent them. Bars keep every value unchanged: prices are read straight into {@link
 * BigDecimal}, as for REST bars. Message types this backend does not use are skipped.
 */
@Component
@Profile("!fake")
class AlpacaStreamParser {

  private static final Logger log = LoggerFactory.getLogger(AlpacaStreamParser.class);

  private static final TypeReference<List<RawMessage>> FRAME = new TypeReference<>() {};

  private final ObjectMapper json;

  AlpacaStreamParser(ObjectMapper json) {
    this.json = json;
  }

  /**
   * The messages in {@code frame}, a JSON array as Alpaca sends it.
   *
   * @throws IllegalArgumentException when {@code frame} is not a JSON array of objects
   */
  List<AlpacaStreamMessage> parse(String frame) {
    List<RawMessage> raw;
    try {
      raw = json.readValue(frame, FRAME);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Not an Alpaca stream frame: " + frame, e);
    }
    List<AlpacaStreamMessage> messages = new ArrayList<>(raw.size());
    for (RawMessage message : raw) {
      AlpacaStreamMessage parsed = message.toMessage();
      if (parsed == null) {
        log.debug("Skipping Alpaca stream message {}", message);
      } else {
        messages.add(parsed);
      }
    }
    return messages;
  }

  /** Every field of every message type this backend reads; the others are ignored. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RawMessage(
      @JsonProperty("T") String type,
      @JsonProperty("msg") String msg,
      @JsonProperty("code") int code,
      @JsonProperty("bars") List<String> bars,
      @JsonProperty("S") String symbol,
      @JsonProperty("t") Instant timestamp,
      @JsonProperty("o") BigDecimal open,
      @JsonProperty("h") BigDecimal high,
      @JsonProperty("l") BigDecimal low,
      @JsonProperty("c") BigDecimal close,
      @JsonProperty("v") long volume,
      @JsonProperty("n") long tradeCount,
      @JsonProperty("vw") BigDecimal vwap) {

    /** The message this is, or null for a type or success message this backend does not use. */
    AlpacaStreamMessage toMessage() {
      if (type == null) {
        return null;
      }
      return switch (type) {
        case "success" ->
            switch (String.valueOf(msg)) {
              case "connected" -> new Connected();
              case "authenticated" -> new Authenticated();
              default -> null;
            };
        case "subscription" -> new Subscribed(bars == null ? List.of() : List.copyOf(bars));
        case "b" ->
            new BarMessage(
                new Bar(symbol, timestamp, open, high, low, close, volume, tradeCount, vwap));
        case "error" -> new ErrorMessage(code, msg);
        default -> null;
      };
    }
  }
}
