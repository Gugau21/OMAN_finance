package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import reactor.test.scheduler.VirtualTimeScheduler;

@ExtendWith(OutputCaptureExtension.class)
class AlpacaStreamClientTest {

  private static final URI STREAM_URL = URI.create("wss://stream.test/v2/iex");
  private static final List<String> SYMBOLS = List.of("AAPL", "MSFT");
  private static final Duration MIN_DELAY = Duration.ofSeconds(1);
  private static final Duration MAX_DELAY = Duration.ofSeconds(4);

  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final FakeOpener opener = new FakeOpener();
  private final LiveBarStream liveBars = new LiveBarStream();
  private final VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
  private final AlpacaStreamClient client =
      new AlpacaStreamClient(
          properties(MIN_DELAY, MAX_DELAY),
          opener,
          new AlpacaStreamParser(json),
          liveBars,
          json,
          scheduler);

  @Test
  void opensTheConfiguredStreamUrl() {
    client.connect(SYMBOLS);

    assertThat(opener.last().url).isEqualTo(STREAM_URL);
  }

  @Test
  void sendsTheKeysOnlyAfterAlpacaSaysConnected() throws IOException {
    open();
    assertThat(opener.last().sender.sent).isEmpty();

    alpaca().onMessageReceived(frame("stream-connected.json"));

    assertThat(opener.last().sender.sent).hasSize(1);
    assertThat(sent(0))
        .isEqualTo(
            json.readTree("{\"action\":\"auth\",\"key\":\"key-id\",\"secret\":\"secret-key\"}"));
  }

  @Test
  void subscribesToTheBarsOfEverySymbolOnlyAfterAuthentication() throws IOException {
    open();
    alpaca().onMessageReceived(frame("stream-connected.json"));
    assertThat(opener.last().sender.sent).hasSize(1);

    alpaca().onMessageReceived(frame("stream-authenticated.json"));

    assertThat(opener.last().sender.sent).hasSize(2);
    assertThat(sent(1))
        .isEqualTo(json.readTree("{\"action\":\"subscribe\",\"bars\":[\"AAPL\",\"MSFT\"]}"));
  }

  @Test
  void publishesEveryBarOfAFrameUnchanged() throws IOException {
    List<Bar> received = new CopyOnWriteArrayList<>();
    liveBars.flux().subscribe(received::add);
    handshake();

    alpaca().onMessageReceived(frame("stream-bars.json"));

    assertThat(received)
        .containsExactlyElementsOf(
            new AlpacaStreamParser(json)
                .parse(frame("stream-bars.json")).stream()
                    .map(message -> ((AlpacaStreamMessage.BarMessage) message).bar())
                    .toList());
  }

  @Test
  void logsTheSymbolsAlpacaDidNotSubscribe(CapturedOutput output) throws IOException {
    client.connect(List.of("AAPL", "MSFT", "GOOG"));
    alpaca().onOpened(opener.last().sender);

    alpaca().onMessageReceived(frame("stream-subscription.json"));

    assertThat(output).contains("Alpaca did not subscribe to the bars of [GOOG]");
  }

  @Test
  void explainsAConnectionLimitError(CapturedOutput output) throws IOException {
    open();

    alpaca().onMessageReceived(frame("stream-error-connection-limit.json"));

    assertThat(output)
        .contains("connection limit exceeded")
        .contains("another process is already connected with these API keys");
  }

  @Test
  void explainsRejectedKeysWithoutLoggingTheSecret(CapturedOutput output) throws IOException {
    open();
    alpaca().onMessageReceived(frame("stream-connected.json"));

    alpaca().onMessageReceived(frame("stream-error-auth-failed.json"));

    assertThat(output).contains("Alpaca stream rejected the API keys").doesNotContain("secret-key");
  }

  @Test
  void skipsAFrameThatIsNotValidAndKeepsReading(CapturedOutput output) throws IOException {
    List<Bar> received = new CopyOnWriteArrayList<>();
    liveBars.flux().subscribe(received::add);
    handshake();

    alpaca().onMessageReceived("not json");
    alpaca().onMessageReceived(frame("stream-bars.json"));

    assertThat(output).contains("Skipping a message from the Alpaca stream that is not valid");
    assertThat(received).hasSize(2);
  }

  @Test
  void closesTheConnectionOnDisconnect() {
    open();

    client.disconnect();

    assertThat(opener.last().sender.closed).isTrue();
  }

  @Test
  void refusesToConnectWithoutSymbols() {
    assertThatThrownBy(() -> client.connect(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesToConnectTwice() {
    client.connect(SYMBOLS);

    assertThatThrownBy(() -> client.connect(SYMBOLS)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reconnectsAfterTheMinDelayWhenAlpacaClosesTheStream() throws IOException {
    handshake();

    alpaca().onClosed(1006, "");
    scheduler.advanceTimeBy(MIN_DELAY.minusMillis(1));
    assertThat(opener.opened).hasSize(1);
    scheduler.advanceTimeBy(Duration.ofMillis(1));

    assertThat(opener.opened).hasSize(2);
    assertThat(opener.last().url).isEqualTo(STREAM_URL);
  }

  @Test
  void authenticatesAndSubscribesAgainAfterReconnecting() throws IOException {
    handshake();
    alpaca().onClosed(1006, "");
    scheduler.advanceTimeBy(MIN_DELAY);

    open(opener.last());
    alpaca().onMessageReceived(frame("stream-connected.json"));
    alpaca().onMessageReceived(frame("stream-authenticated.json"));

    assertThat(opener.last().sender.sent).hasSize(2);
    assertThat(sent(0).get("action").asText()).isEqualTo("auth");
    assertThat(sent(1))
        .isEqualTo(json.readTree("{\"action\":\"subscribe\",\"bars\":[\"AAPL\",\"MSFT\"]}"));
  }

  @Test
  void reconnectsWhenTheConnectionCannotBeOpened() {
    client.connect(SYMBOLS);

    alpaca().onFailed(new ConnectException("Connection refused"));
    scheduler.advanceTimeBy(MIN_DELAY);

    assertThat(opener.opened).hasSize(2);
  }

  @Test
  void doublesTheDelayAfterEachFailedAttemptUpToTheMax() {
    client.connect(SYMBOLS);

    assertThat(waitsBeforeReconnecting()).isEqualTo(Duration.ofSeconds(1));
    assertThat(waitsBeforeReconnecting()).isEqualTo(Duration.ofSeconds(2));
    assertThat(waitsBeforeReconnecting()).isEqualTo(Duration.ofSeconds(4));
    assertThat(waitsBeforeReconnecting()).isEqualTo(Duration.ofSeconds(4));
  }

  @Test
  void resetsTheDelayOnceSubscribed() throws IOException {
    client.connect(SYMBOLS);
    waitsBeforeReconnecting();
    waitsBeforeReconnecting();

    open(opener.last());
    alpaca().onMessageReceived(frame("stream-subscription.json"));

    assertThat(waitsBeforeReconnecting()).isEqualTo(MIN_DELAY);
  }

  @Test
  void logsTheReconnectDelay(CapturedOutput output) throws IOException {
    handshake();

    alpaca().onClosed(1006, "gone");

    assertThat(output).contains("Alpaca stream closed: 1006 gone, reconnecting in PT1S");
  }

  @Test
  void closesTheConnectionWhenAlpacaSendsAnErrorBeforeTheSubscription() throws IOException {
    open();

    alpaca().onMessageReceived(frame("stream-error-connection-limit.json"));

    assertThat(opener.last().sender.closed).isTrue();
  }

  @Test
  void keepsTheConnectionWhenAlpacaSendsAnErrorAfterTheSubscription() throws IOException {
    handshake();

    alpaca().onMessageReceived(frame("stream-error-connection-limit.json"));

    assertThat(opener.last().sender.closed).isFalse();
  }

  @Test
  void doesNotReconnectAfterDisconnect() {
    open();
    client.disconnect();

    alpaca().onClosed(1000, "");
    scheduler.advanceTimeBy(Duration.ofHours(1));

    assertThat(opener.opened).hasSize(1);
  }

  @Test
  void cancelsAPendingReconnectOnDisconnect() {
    open();
    alpaca().onClosed(1006, "");

    client.disconnect();
    scheduler.advanceTimeBy(Duration.ofHours(1));

    assertThat(opener.opened).hasSize(1);
  }

  @Test
  void ignoresTheEndOfAConnectionThatWasAlreadyReplaced() {
    open();
    FakeConnection first = opener.last();
    first.listener.onClosed(1006, "");
    scheduler.advanceTimeBy(MIN_DELAY);
    assertThat(opener.opened).hasSize(2);

    first.listener.onFailed(new IOException("late failure of the first connection"));
    scheduler.advanceTimeBy(Duration.ofHours(1));

    assertThat(opener.opened).hasSize(2);
  }

  @Test
  void refusesAMaxDelayBelowTheMinDelay() {
    assertThatThrownBy(
            () ->
                new AlpacaStreamClient(
                    properties(Duration.ofSeconds(10), Duration.ofSeconds(5)),
                    opener,
                    new AlpacaStreamParser(json),
                    liveBars,
                    json,
                    scheduler))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stream-reconnect-max-delay");
  }

  @Test
  void refusesAZeroMinDelay() {
    assertThatThrownBy(
            () ->
                new AlpacaStreamClient(
                    properties(Duration.ZERO, MAX_DELAY),
                    opener,
                    new AlpacaStreamParser(json),
                    liveBars,
                    json,
                    scheduler))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stream-reconnect-min-delay");
  }

  /**
   * Fails the current connection and returns how long the client waits before opening the next one,
   * to the second.
   */
  private Duration waitsBeforeReconnecting() {
    int before = opener.opened.size();
    alpaca().onFailed(new ConnectException("Connection refused"));
    for (int seconds = 1; seconds <= 3600; seconds++) {
      scheduler.advanceTimeBy(Duration.ofSeconds(1));
      if (opener.opened.size() > before) {
        return Duration.ofSeconds(seconds);
      }
    }
    throw new AssertionError("The client did not reconnect within an hour");
  }

  /** The listener of the last connection the client opened: the test plays Alpaca through it. */
  private WebSocketListener alpaca() {
    return opener.last().listener;
  }

  private void open() {
    client.connect(SYMBOLS);
    open(opener.last());
  }

  private static void open(FakeConnection connection) {
    connection.listener.onOpened(connection.sender);
  }

  private void handshake() throws IOException {
    open();
    alpaca().onMessageReceived(frame("stream-connected.json"));
    alpaca().onMessageReceived(frame("stream-authenticated.json"));
    alpaca().onMessageReceived(frame("stream-subscription.json"));
  }

  private JsonNode sent(int index) throws IOException {
    return json.readTree(opener.last().sender.sent.get(index));
  }

  private static String frame(String name) throws IOException {
    return new ClassPathResource("alpaca/" + name).getContentAsString(StandardCharsets.UTF_8);
  }

  private static AlpacaProperties properties(Duration minDelay, Duration maxDelay) {
    return new AlpacaProperties(
        "key-id",
        "secret-key",
        null,
        null,
        STREAM_URL,
        Duration.ofSeconds(1),
        null,
        null,
        null,
        null,
        minDelay,
        maxDelay);
  }

  /** Records every connection the client opens. */
  private static final class FakeOpener implements WebSocketOpener {

    private final List<FakeConnection> opened = new CopyOnWriteArrayList<>();

    @Override
    public void open(URI url, WebSocketListener listener) {
      opened.add(new FakeConnection(url, listener, new FakeSender()));
    }

    FakeConnection last() {
      return opened.getLast();
    }
  }

  private record FakeConnection(URI url, WebSocketListener listener, FakeSender sender) {}

  private static final class FakeSender implements WebSocketSender {

    private final List<String> sent = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    @Override
    public void send(String text) {
      sent.add(text);
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
