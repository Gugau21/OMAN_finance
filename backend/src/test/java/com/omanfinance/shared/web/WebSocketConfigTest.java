package com.omanfinance.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.omanfinance.TestRedisDatabase;
import com.omanfinance.marketingestion.internal.StubAlpacaClock;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {TestRedisDatabase.PROPERTY, "app.cors.allowed-origins=http://localhost"})
@ActiveProfiles("fake")
@Import(StubAlpacaClock.Config.class)
class WebSocketConfigTest {

  private static final long TIMEOUT_SECONDS = 5;
  private static final String MARKET_STATUS = "/topic/market/status";

  @LocalServerPort int port;

  @Autowired SimpMessagingTemplate messagingTemplate;

  private ThreadPoolTaskScheduler clientScheduler;
  private WebSocketStompClient client;

  @BeforeEach
  void createClient() {
    clientScheduler = new ThreadPoolTaskScheduler();
    clientScheduler.initialize();
    client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setTaskScheduler(clientScheduler);
    client.setMessageConverter(new StringMessageConverter());
  }

  @AfterEach
  void stopClient() {
    client.stop();
    clientScheduler.shutdown();
  }

  @AfterAll
  static void deleteWhatStartupSaved(@Autowired StringRedisTemplate redis) {
    TestRedisDatabase.flush(redis);
  }

  @Test
  void connectsOverPlainWebSocketAndAgreesOnTenSecondHeartBeats() throws Exception {
    Connection connection = connect(new WebSocketHttpHeaders());
    try {
      assertThat(connection.session.isConnected()).isTrue();
      assertThat(connection.connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getHeartbeat())
          .containsExactly(WebSocketConfig.HEARTBEAT_MILLIS, WebSocketConfig.HEARTBEAT_MILLIS);
    } finally {
      connection.session.disconnect();
    }
  }

  @Test
  void acceptsAnAllowedOrigin() throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setOrigin("http://localhost");

    Connection connection = connect(headers);
    try {
      assertThat(connection.session.isConnected()).isTrue();
    } finally {
      connection.session.disconnect();
    }
  }

  @Test
  void rejectsAnotherOrigin() {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setOrigin("http://evil.example");

    assertThatThrownBy(() -> connect(headers)).isInstanceOf(ExecutionException.class);
  }

  @Test
  void rejectsSendToApp() throws Exception {
    Connection sender = connect(new WebSocketHttpHeaders());

    sender.session.send("/app/anything", "hello");

    assertThat(sender.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getFirst("message"))
        .contains("SEND to /app/anything is not allowed");
  }

  @Test
  void rejectsSendToTopicSoOtherSubscribersGetNothing() throws Exception {
    Connection subscriber = connect(new WebSocketHttpHeaders());
    BlockingQueue<String> received = subscribe(subscriber.session, MARKET_STATUS);
    Connection sender = connect(new WebSocketHttpHeaders());

    sender.session.send(MARKET_STATUS, "{\"isOpen\":false}");

    assertThat(sender.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getFirst("message"))
        .contains("SEND to " + MARKET_STATUS + " is not allowed");
    messagingTemplate.convertAndSend(MARKET_STATUS, "from the backend");
    assertThat(received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("from the backend");
    subscriber.session.disconnect();
  }

  /**
   * Subscribes and waits until the broker delivers to the subscription, so nothing sent afterwards
   * can arrive before the subscription is registered.
   */
  private BlockingQueue<String> subscribe(StompSession session, String destination) {
    BlockingQueue<String> received = new LinkedBlockingQueue<>();
    session.subscribe(
        destination,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return String.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            received.add((String) payload);
          }
        });
    await()
        .atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
        .until(
            () -> {
              messagingTemplate.convertAndSend(destination, "ready?");
              return received.poll(100, TimeUnit.MILLISECONDS) != null;
            });
    received.clear();
    return received;
  }

  private Connection connect(WebSocketHttpHeaders headers) throws Exception {
    CompletableFuture<StompHeaders> connected = new CompletableFuture<>();
    CompletableFuture<StompHeaders> error = new CompletableFuture<>();
    StompSession session =
        client
            .connectAsync(
                "ws://localhost:" + port + "/ws",
                headers,
                new StompSessionHandlerAdapter() {
                  @Override
                  public void afterConnected(StompSession session, StompHeaders headers) {
                    connected.complete(headers);
                  }

                  @Override
                  public void handleFrame(StompHeaders headers, Object payload) {
                    error.complete(headers);
                  }
                })
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    return new Connection(session, connected, error);
  }

  /**
   * A connected client, the headers of its {@code CONNECTED} frame and those of the first {@code
   * ERROR} frame it gets. The session handler only sees {@link StompCommand#ERROR} frames, as
   * messages go to their subscription's handler.
   */
  private record Connection(
      StompSession session,
      CompletableFuture<StompHeaders> connected,
      CompletableFuture<StompHeaders> error) {}
}
