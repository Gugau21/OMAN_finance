package com.omanfinance.marketingestion.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Authenticated;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.BarMessage;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Connected;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.ErrorMessage;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Subscribed;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Receives live minute bars from the Alpaca market data WebSocket ({@code alpaca.stream-url}) and
 * hands each one to {@link LiveBarStream}, values unchanged.
 *
 * <p>After the WebSocket opens, Alpaca sends {@code connected}; the client answers with the API
 * keys, and once Alpaca sends {@code authenticated} it subscribes to the bars of the tracked
 * symbols. Errors Alpaca sends are logged with what they mean; the secret key is never logged.
 *
 * <p>When the connection closes or fails, the client opens a new one, authenticates and subscribes
 * again, until {@link #disconnect} is called. The first reconnect waits {@code
 * alpaca.stream-reconnect-min-delay}; each one after that doubles the wait, up to {@code
 * alpaca.stream-reconnect-max-delay}, and a successful subscription resets it. An error from Alpaca
 * before the subscription succeeds (wrong keys, connection limit) closes the connection, so it is
 * retried the same way instead of staying open without bars.
 */
@Component
@Profile("!fake")
class AlpacaStreamClient {

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
  private final Scheduler scheduler;
  private final Duration minReconnectDelay;
  private final Duration maxReconnectDelay;

  private volatile List<String> symbols = List.of();

  // Guarded by this.
  private boolean running;
  private Connection connection;
  private Disposable pendingReconnect = Disposables.disposed();
  private Duration reconnectDelay;

  @Autowired
  AlpacaStreamClient(
      AlpacaProperties properties,
      WebSocketOpener opener,
      AlpacaStreamParser parser,
      LiveBarStream liveBars,
      ObjectMapper json) {
    this(properties, opener, parser, liveBars, json, Schedulers.newSingle("alpaca-stream", true));
  }

  /**
   * @param scheduler runs the reconnects; disposed by {@link #stop}
   * @throws IllegalArgumentException when the reconnect delays are not positive or the max is below
   *     the min
   */
  AlpacaStreamClient(
      AlpacaProperties properties,
      WebSocketOpener opener,
      AlpacaStreamParser parser,
      LiveBarStream liveBars,
      ObjectMapper json,
      Scheduler scheduler) {
    Duration min = properties.streamReconnectMinDelay();
    Duration max = properties.streamReconnectMaxDelay();
    if (min == null || min.isNegative() || min.isZero()) {
      throw new IllegalArgumentException(
          "alpaca.stream-reconnect-min-delay must be positive, was " + min);
    }
    if (max == null || max.compareTo(min) < 0) {
      throw new IllegalArgumentException(
          "alpaca.stream-reconnect-max-delay must be at least %s, was %s".formatted(min, max));
    }
    this.properties = properties;
    this.opener = opener;
    this.parser = parser;
    this.liveBars = liveBars;
    this.json = json;
    this.scheduler = scheduler;
    this.minReconnectDelay = min;
    this.maxReconnectDelay = max;
    this.reconnectDelay = min;
  }

  /**
   * Opens the stream and, once authenticated, subscribes to the minute bars of {@code symbols}.
   * Returns at once. From now on a dropped connection is opened again, until {@link #disconnect}.
   *
   * @throws IllegalArgumentException when {@code symbols} is empty
   * @throws IllegalStateException when already connected
   */
  synchronized void connect(List<String> symbols) {
    if (symbols.isEmpty()) {
      throw new IllegalArgumentException("No symbols to subscribe to");
    }
    if (running) {
      throw new IllegalStateException("Already connected to the Alpaca stream");
    }
    this.symbols = List.copyOf(symbols);
    running = true;
    reconnectDelay = minReconnectDelay;
    log.info(
        "Connecting to the Alpaca stream at {} for {} symbols",
        properties.streamUrl(),
        symbols.size());
    open();
  }

  /** Closes the stream, if it is open, and stops reconnecting. */
  synchronized void disconnect() {
    running = false;
    pendingReconnect.dispose();
    if (connection != null) {
      connection.close();
      connection = null;
    }
  }

  /** Closes the stream and cancels a pending reconnect when the application shuts down. */
  @PreDestroy
  void stop() {
    disconnect();
    scheduler.dispose();
  }

  /** Opens a new connection; the one before it, if any, has already ended. */
  private void open() {
    Connection next = new Connection();
    connection = next;
    opener.open(properties.streamUrl(), next);
  }

  /**
   * Schedules a reconnect when {@code ended} is the current connection and {@link #disconnect} has
   * not been called; a connection that was already replaced or closed by us is ignored.
   */
  private synchronized void connectionEnded(Connection ended, String what) {
    if (ended != connection || !running) {
      log.info("Alpaca stream {}", what);
      return;
    }
    connection = null;
    Duration delay = reconnectDelay;
    reconnectDelay = min(delay.multipliedBy(2), maxReconnectDelay);
    log.warn("Alpaca stream {}, reconnecting in {}", what, delay);
    pendingReconnect = scheduler.schedule(this::reconnect, delay.toMillis(), TimeUnit.MILLISECONDS);
  }

  private synchronized void reconnect() {
    if (!running) {
      return;
    }
    log.info("Reconnecting to the Alpaca stream at {}", properties.streamUrl());
    open();
  }

  private synchronized void resetReconnectDelay() {
    reconnectDelay = minReconnectDelay;
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  private static void logError(ErrorMessage error) {
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

  /**
   * One WebSocket connection, from opening to its close or failure. Each reconnect gets a new one,
   * so late events from an old connection cannot be mistaken for the current one.
   */
  private final class Connection implements WebSocketListener {

    private volatile WebSocketSender sender;
    private volatile boolean subscribed;

    @Override
    public void onOpened(WebSocketSender opened) {
      this.sender = opened;
      log.debug("Alpaca stream opened, waiting for connected");
    }

    @Override
    public void onMessageReceived(String message) {
      log.trace("Alpaca stream sent {}", message);
      List<AlpacaStreamMessage> messages;
      try {
        messages = parser.parse(message);
      } catch (IllegalArgumentException e) {
        log.warn("Skipping a message from the Alpaca stream that is not valid: {}", e.getMessage());
        return;
      }
      messages.forEach(this::handle);
    }

    @Override
    public void onClosed(int statusCode, String reason) {
      connectionEnded(this, "closed: %d %s".formatted(statusCode, reason));
    }

    @Override
    public void onFailed(Throwable error) {
      connectionEnded(this, "failed: " + error);
    }

    void close() {
      WebSocketSender current = sender;
      if (current != null) {
        current.close();
      }
    }

    private void handle(AlpacaStreamMessage message) {
      switch (message) {
        case Connected connected -> authenticate();
        case Authenticated authenticated -> subscribe();
        case Subscribed subscription -> onSubscribed(subscription);
        case BarMessage bar -> {
          log.debug("Live bar {}", bar.bar());
          liveBars.publish(bar.bar());
        }
        case ErrorMessage error -> onError(error);
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

    private void onSubscribed(Subscribed subscription) {
      subscribed = true;
      resetReconnectDelay();
      log.info("Subscribed to live bars of {} symbols", subscription.bars().size());
      List<String> missing =
          symbols.stream().filter(s -> !subscription.bars().contains(s)).toList();
      if (!missing.isEmpty()) {
        log.warn("Alpaca did not subscribe to the bars of {}", missing);
      }
    }

    /** Before the subscription, an error leaves the connection without bars, so it is closed. */
    private void onError(ErrorMessage error) {
      logError(error);
      if (!subscribed) {
        close();
      }
    }
  }
}
