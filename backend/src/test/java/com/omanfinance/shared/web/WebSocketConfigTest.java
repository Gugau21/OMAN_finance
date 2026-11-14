package com.omanfinance.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omanfinance.TestRedisDatabase;
import com.omanfinance.marketingestion.internal.StubAlpacaClock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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

  @LocalServerPort int port;

  private ThreadPoolTaskScheduler clientScheduler;
  private WebSocketStompClient client;

  /** The headers of the {@code CONNECTED} frame of the last {@link #connect}. */
  private CompletableFuture<StompHeaders> connectedHeaders;

  @BeforeEach
  void createClient() {
    clientScheduler = new ThreadPoolTaskScheduler();
    clientScheduler.initialize();
    client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setTaskScheduler(clientScheduler);
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
    StompSession session = connect(new WebSocketHttpHeaders());
    try {
      assertThat(session.isConnected()).isTrue();
      assertThat(connectedHeaders.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getHeartbeat())
          .containsExactly(WebSocketConfig.HEARTBEAT_MILLIS, WebSocketConfig.HEARTBEAT_MILLIS);
    } finally {
      session.disconnect();
    }
  }

  @Test
  void acceptsAnAllowedOrigin() throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setOrigin("http://localhost");

    StompSession session = connect(headers);
    try {
      assertThat(session.isConnected()).isTrue();
    } finally {
      session.disconnect();
    }
  }

  @Test
  void rejectsAnotherOrigin() {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setOrigin("http://evil.example");

    assertThatThrownBy(() -> connect(headers)).isInstanceOf(ExecutionException.class);
  }

  private StompSession connect(WebSocketHttpHeaders headers) throws Exception {
    connectedHeaders = new CompletableFuture<>();
    return client
        .connectAsync(
            "ws://localhost:" + port + "/ws",
            headers,
            new StompSessionHandlerAdapter() {
              @Override
              public void afterConnected(StompSession session, StompHeaders headers) {
                connectedHeaders.complete(headers);
              }
            })
        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }
}
