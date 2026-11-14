package com.omanfinance.marketingestion.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Authenticated;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.BarMessage;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Connected;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.ErrorMessage;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Subscribed;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Receives live minute bars from the Alpaca market data WebSocket ({@code alpaca.stream-url}) and
 * hands each one to {@link LiveBarStream}, values unchanged.
 *
 * <p>After the WebSocket opens, Alpaca sends {@code connected}; the client answers with the API
 * keys, and once Alpaca sends {@code authenticated} it subscribes to the bars of the tracked
 * symbols. Errors Alpaca sends are logged with what they mean; the secret key is never logged.
 */
@Component
@Profile("!fake")
class AlpacaStreamClient implements WebSocketListener {

  /** The API keys were wrong. */
  static final int AUTH_FAILED = 402;

  /** Another connection already uses these API keys; Alpaca allows one per key. */
  static final int CONNECTION_LIMIT_EXCEEDED = 406;

  private static final Logger log = LoggerFactory.getLogger(AlpacaStreamClient.class);

  private final AlpacaProperties properties;
  private final WebSocketOpener opener;
  private final AlpacaStreamParser parser;
  private final LiveBarStream liveBars;
  private final ObjectMapper json;

  private volatile List<String> symbols = List.of();
  private volatile WebSocketSender sender;

  AlpacaStreamClient(
      AlpacaProperties properties,
      WebSocketOpener opener,
      AlpacaStreamParser parser,
      LiveBarStream liveBars,
      ObjectMapper json) {
    this.properties = properties;
    this.opener = opener;
    this.parser = parser;
    this.liveBars = liveBars;
    this.json = json;
  }

  /**
   * Opens the stream and, once authenticated, subscribes to the minute bars of {@code symbols}.
   * Returns at once.
   *
   * @throws IllegalArgumentException when {@code symbols} is empty
   */
  void connect(List<String> symbols) {
    if (symbols.isEmpty()) {
      throw new IllegalArgumentException("No symbols to subscribe to");
    }
    this.symbols = List.copyOf(symbols);
    log.info(
        "Connecting to the Alpaca stream at {} for {} symbols",
        properties.streamUrl(),
        symbols.size());
    opener.open(properties.streamUrl(), this);
  }

  /** Closes the stream, if it is open. */
  void disconnect() {
    WebSocketSender current = sender;
    if (current != null) {
      current.close();
    }
  }

  @Override
  public void onOpened(WebSocketSender opened) {
    this.sender = opened;
    log.debug("Alpaca stream opened, waiting for connected");
  }

  @Override
  public void onMessageReceived(String json) {
    log.trace("Alpaca stream sent {}", json);
    List<AlpacaStreamMessage> messages;
    try {
      messages = parser.parse(json);
    } catch (IllegalArgumentException e) {
      log.warn("Skipping a message from the Alpaca stream that is not valid: {}", e.getMessage());
      return;
    }
    messages.forEach(this::handle);
  }

  @Override
  public void onClosed(int statusCode, String reason) {
    log.warn("Alpaca stream closed: {} {}", statusCode, reason);
  }

  @Override
  public void onFailed(Throwable error) {
    log.warn("Alpaca stream failed: {}", error.toString());
  }

  private void handle(AlpacaStreamMessage message) {
    switch (message) {
      case Connected connected -> authenticate();
      case Authenticated authenticated -> subscribe();
      case Subscribed subscribed -> logSubscription(subscribed);
      case BarMessage bar -> {
        log.debug("Live bar {}", bar.bar());
        liveBars.publish(bar.bar());
      }
      case ErrorMessage error -> logError(error);
    }
  }

  private void authenticate() {
    log.debug("Alpaca stream connected, authenticating");
    send(
        json.createObjectNode()
            .put("action", "auth")
            .put("key", properties.apiKeyId())
            .put("secret", properties.apiSecretKey()));
  }

  private void subscribe() {
    log.debug("Alpaca stream authenticated, subscribing to bars of {} symbols", symbols.size());
    ObjectNode message = json.createObjectNode().put("action", "subscribe");
    ArrayNode bars = message.putArray("bars");
    symbols.forEach(bars::add);
    send(message);
  }

  private void send(ObjectNode message) {
    WebSocketSender current = sender;
    if (current != null) {
      current.send(message.toString());
    }
  }

  private void logSubscription(Subscribed subscribed) {
    log.info("Subscribed to live bars of {} symbols", subscribed.bars().size());
    List<String> missing = symbols.stream().filter(s -> !subscribed.bars().contains(s)).toList();
    if (!missing.isEmpty()) {
      log.warn("Alpaca did not subscribe to the bars of {}", missing);
    }
  }

  private void logError(ErrorMessage error) {
    switch (error.code()) {
      case AUTH_FAILED ->
          log.error(
              "Alpaca stream rejected the API keys ({} {}): check ALPACA_API_KEY_ID and"
                  + " ALPACA_API_SECRET_KEY",
              error.code(),
              error.msg());
      case CONNECTION_LIMIT_EXCEEDED ->
          log.error(
              "Alpaca stream connection limit exceeded ({} {}): another process is already"
                  + " connected with these API keys, e.g. a second backend or a teammate's local"
                  + " run",
              error.code(),
              error.msg());
      default -> log.error("Alpaca stream error {}: {}", error.code(), error.msg());
    }
  }
}
