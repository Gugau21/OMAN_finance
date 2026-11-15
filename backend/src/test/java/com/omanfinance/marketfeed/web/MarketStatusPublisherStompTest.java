package com.omanfinance.marketfeed.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.omanfinance.TestRedisDatabase;
import com.omanfinance.marketingestion.api.dto.Clock;
import com.omanfinance.marketingestion.internal.StubAlpacaClock;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** A STOMP client subscribed through {@code /ws} gets the status as {@code stomp_contract.yaml}. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = TestRedisDatabase.PROPERTY)
@ActiveProfiles("fake")
@Import(StubAlpacaClock.Config.class)
class MarketStatusPublisherStompTest {

  private static final long TIMEOUT_SECONDS = 5;

  private static final Clock CLOSED =
      new Clock(
          Instant.parse("2026-11-09T22:15:30Z"),
          false,
          Instant.parse("2026-11-10T14:30:00Z"),
          Instant.parse("2026-11-10T21:00:00Z"));

  @LocalServerPort int port;

  @Autowired MarketStatusPublisher publisher;

  @AfterAll
  static void deleteWhatStartupSaved(@Autowired StringRedisTemplate redis) {
    TestRedisDatabase.flush(redis);
  }

  @Test
  void aSubscriberGetsTheStatusWithTheSamePayloadAsRest() throws Exception {
    ThreadPoolTaskScheduler clientScheduler = new ThreadPoolTaskScheduler();
    clientScheduler.initialize();
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setTaskScheduler(clientScheduler);
    client.setMessageConverter(new MappingJackson2MessageConverter());
    try {
      StompSession session =
          client
              .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
              .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      BlockingQueue<Map<?, ?>> received = new LinkedBlockingQueue<>();
      session.subscribe(
          MarketStatusPublisher.DESTINATION,
          new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
              return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
              received.add((Map<?, ?>) payload);
            }
          });

      // Publish until it arrives, as the broker registers the subscription asynchronously.
      AtomicReference<Map<?, ?>> payload = new AtomicReference<>();
      await()
          .atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
          .until(
              () -> {
                publisher.publish(CLOSED);
                payload.set(received.poll(100, TimeUnit.MILLISECONDS));
                return payload.get() != null;
              });

      // The same fields and values as MarketControllerTest checks for GET /api/market/status.
      assertThat(payload.get())
          .isEqualTo(
              Map.of(
                  "timestamp", "2026-11-09T22:15:30Z",
                  "isOpen", false,
                  "nextOpen", "2026-11-10T14:30:00Z",
                  "nextClose", "2026-11-10T21:00:00Z"));
      session.disconnect();
    } finally {
      client.stop();
      clientScheduler.shutdown();
    }
  }
}
