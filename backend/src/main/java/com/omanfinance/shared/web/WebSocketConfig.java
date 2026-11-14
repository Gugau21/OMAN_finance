package com.omanfinance.shared.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * The STOMP endpoint from {@code stomp_contract.yaml}: STOMP 1.2 over a plain WebSocket at {@code
 * /ws} (no SockJS), with a simple broker on {@code /topic}.
 *
 * <p>Heart-beats are {@code 10000,10000}. Nginx closes a WebSocket after 10 minutes without
 * traffic, and the stock topics are silent while the market is closed.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  /** Milliseconds between heart-beats, sent and expected. */
  static final long HEARTBEAT_MILLIS = 10_000;

  private final String[] allowedOrigins;

  private TaskScheduler messageBrokerTaskScheduler;

  public WebSocketConfig(@Value(AllowedOrigins.PROPERTY) String allowedOrigins) {
    this.allowedOrigins = AllowedOrigins.parse(allowedOrigins);
  }

  /**
   * The scheduler Spring creates for the broker. Lazy, because this configurer is read while that
   * scheduler's own configuration is being built.
   */
  @Autowired
  void setMessageBrokerTaskScheduler(
      @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler) {
    this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry
        .enableSimpleBroker("/topic")
        .setHeartbeatValue(new long[] {HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
        .setTaskScheduler(messageBrokerTaskScheduler);
  }
}
