package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import java.io.IOException;
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

@ExtendWith(OutputCaptureExtension.class)
class AlpacaStreamClientTest {

  private static final URI STREAM_URL = URI.create("wss://stream.test/v2/iex");
  private static final List<String> SYMBOLS = List.of("AAPL", "MSFT");

  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final FakeOpener opener = new FakeOpener();
  private final LiveBarStream liveBars = new LiveBarStream();
  private final AlpacaStreamClient client =
      new AlpacaStreamClient(properties(), opener, new AlpacaStreamParser(json), liveBars, json);

  @Test
  void opensTheConfiguredStreamUrl() {
    client.connect(SYMBOLS);

    assertThat(opener.url).isEqualTo(STREAM_URL);
  }

  @Test
  void sendsTheKeysOnlyAfterAlpacaSaysConnected() throws IOException {
    open();
    assertThat(opener.sender.sent).isEmpty();

    client.onMessageReceived(frame("stream-connected.json"));

    assertThat(opener.sender.sent).hasSize(1);
    assertThat(sent(0))
        .isEqualTo(
            json.readTree("{\"action\":\"auth\",\"key\":\"key-id\",\"secret\":\"secret-key\"}"));
  }

  @Test
  void subscribesToTheBarsOfEverySymbolOnlyAfterAuthentication() throws IOException {
    open();
    client.onMessageReceived(frame("stream-connected.json"));
    assertThat(opener.sender.sent).hasSize(1);

    client.onMessageReceived(frame("stream-authenticated.json"));

    assertThat(opener.sender.sent).hasSize(2);
    assertThat(sent(1))
        .isEqualTo(json.readTree("{\"action\":\"subscribe\",\"bars\":[\"AAPL\",\"MSFT\"]}"));
  }

  @Test
  void publishesEveryBarOfAFrameUnchanged() throws IOException {
    List<Bar> received = new CopyOnWriteArrayList<>();
    liveBars.flux().subscribe(received::add);
    handshake();

    client.onMessageReceived(frame("stream-bars.json"));

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
    opener.listener.onOpened(opener.sender);

    client.onMessageReceived(frame("stream-subscription.json"));

    assertThat(output).contains("Alpaca did not subscribe to the bars of [GOOG]");
  }

  @Test
  void explainsAConnectionLimitError(CapturedOutput output) throws IOException {
    open();

    client.onMessageReceived(frame("stream-error-connection-limit.json"));

    assertThat(output)
        .contains("connection limit exceeded")
        .contains("another process is already connected with these API keys");
  }

  @Test
  void explainsRejectedKeysWithoutLoggingTheSecret(CapturedOutput output) throws IOException {
    open();
    client.onMessageReceived(frame("stream-connected.json"));

    client.onMessageReceived(frame("stream-error-auth-failed.json"));

    assertThat(output).contains("Alpaca stream rejected the API keys").doesNotContain("secret-key");
  }

  @Test
  void skipsAFrameThatIsNotValidAndKeepsReading(CapturedOutput output) throws IOException {
    List<Bar> received = new CopyOnWriteArrayList<>();
    liveBars.flux().subscribe(received::add);
    handshake();

    client.onMessageReceived("not json");
    client.onMessageReceived(frame("stream-bars.json"));

    assertThat(output).contains("Skipping a message from the Alpaca stream that is not valid");
    assertThat(received).hasSize(2);
  }

  @Test
  void closesTheConnectionOnDisconnect() {
    open();

    client.disconnect();

    assertThat(opener.sender.closed).isTrue();
  }

  @Test
  void refusesToConnectWithoutSymbols() {
    assertThatThrownBy(() -> client.connect(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void open() {
    client.connect(SYMBOLS);
    opener.listener.onOpened(opener.sender);
  }

  private void handshake() throws IOException {
    open();
    client.onMessageReceived(frame("stream-connected.json"));
    client.onMessageReceived(frame("stream-authenticated.json"));
    client.onMessageReceived(frame("stream-subscription.json"));
  }

  private JsonNode sent(int index) throws IOException {
    return json.readTree(opener.sender.sent.get(index));
  }

  private static String frame(String name) throws IOException {
    return new ClassPathResource("alpaca/" + name).getContentAsString(StandardCharsets.UTF_8);
  }

  private static AlpacaProperties properties() {
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
        Duration.ofSeconds(1),
        Duration.ofSeconds(60));
  }

  /** Records what the client opens; the test plays Alpaca by calling the listener. */
  private static final class FakeOpener implements WebSocketOpener {

    private final FakeSender sender = new FakeSender();
    private URI url;
    private WebSocketListener listener;

    @Override
    public void open(URI url, WebSocketListener listener) {
      this.url = url;
      this.listener = listener;
    }
  }

  private static final class FakeSender implements WebSocketSender {

    private final List<String> sent = new CopyOnWriteArrayList<>();
    private boolean closed;

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
