package com.omanfinance.marketingestion.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Connects the WebSocket client that comes with Java ({@code java.net.http}) to our three small
 * interfaces, so no extra library is needed:
 *
 * <ul>
 *   <li>{@link WebSocketOpener}: {@link #open} opens the WebSocket, giving up after {@code
 *       alpaca.connect-timeout};
 *   <li>{@link WebSocketSender}: once open, the listener gets a sender that sends messages one
 *       after the other;
 *   <li>{@link WebSocketListener}: every whole message the server sends, and the close or failure,
 *       is passed to the listener.
 * </ul>
 */
@Component
@Profile("!fake")
class WebSocketAdapter implements WebSocketOpener {

  private final HttpClient http;
  private final Duration connectTimeout;

  WebSocketAdapter(AlpacaProperties properties) {
    this.connectTimeout = properties.connectTimeout();
    this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
  }

  @Override
  public void open(URI url, WebSocketListener listener) {
    http.newWebSocketBuilder()
        .connectTimeout(connectTimeout)
        .buildAsync(url, new FrameJoiner(listener))
        .whenComplete(
            (webSocket, error) -> {
              if (error != null) {
                listener.onFailed(unwrap(error));
              }
            });
  }

  private static Throwable unwrap(Throwable error) {
    return error instanceof CompletionException && error.getCause() != null
        ? error.getCause()
        : error;
  }

  /** Joins text fragments into whole frames and asks for the next message after each one. */
  private static final class FrameJoiner implements WebSocket.Listener {

    private final WebSocketListener listener;
    private final StringBuilder frame = new StringBuilder();

    FrameJoiner(WebSocketListener listener) {
      this.listener = listener;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      listener.onOpened(new JdkSender(webSocket));
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      frame.append(data);
      if (last) {
        String message = frame.toString();
        frame.setLength(0);
        listener.onMessageReceived(message);
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      listener.onClosed(statusCode, reason);
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      listener.onFailed(error);
    }
  }

  /**
   * Sends one message after the other: the JDK client refuses a send while the previous one is
   * still in progress.
   */
  private static final class JdkSender implements WebSocketSender {

    private final WebSocket webSocket;
    private CompletableFuture<WebSocket> lastSend = CompletableFuture.completedFuture(null);

    JdkSender(WebSocket webSocket) {
      this.webSocket = webSocket;
    }

    @Override
    public synchronized void send(String text) {
      lastSend = afterLastSend().thenCompose(ignored -> webSocket.sendText(text, true));
    }

    @Override
    public synchronized void close() {
      lastSend =
          afterLastSend().thenCompose(ignored -> webSocket.sendClose(WebSocket.NORMAL_CLOSURE, ""));
    }

    /** The last send, failed or not: a failed send is reported by the listener's onFailed. */
    private CompletableFuture<WebSocket> afterLastSend() {
      return lastSend.exceptionally(error -> webSocket);
    }
  }
}
