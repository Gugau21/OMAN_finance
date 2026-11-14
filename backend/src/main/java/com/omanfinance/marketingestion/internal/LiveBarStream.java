package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.dto.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Sends each live bar that {@code AlpacaStreamClient} receives to every current subscriber of
 * {@code streamBars()}, inside the backend. It does not connect anywhere itself.
 *
 * <p>Bars are not stored or replayed: a subscriber gets only the bars published after it
 * subscribed, and a bar published while nobody listens is dropped. The stream never completes or
 * fails, not when the last subscriber leaves and not when Alpaca disconnects, so a subscriber never
 * has to subscribe again.
 *
 * <p>A subscriber that throws or falls behind loses only its own bars; the others still get every
 * bar.
 */
@Component
@Profile("!fake")
class LiveBarStream {

  private static final Logger log = LoggerFactory.getLogger(LiveBarStream.class);

  /**
   * Direct, so nothing is buffered or replayed and it stays open with no subscribers; best effort,
   * so a subscriber without demand is skipped instead of holding back the others.
   */
  private final Sinks.Many<Bar> sink = Sinks.many().multicast().directBestEffort();

  /** The shared stream of live bars; never completes. */
  Flux<Bar> flux() {
    return sink.asFlux();
  }

  /**
   * Sends {@code bar} to every current subscriber. A subscriber that throws is cancelled by Reactor
   * and the exception does not reach this call, so the caller's WebSocket thread keeps reading.
   * Synchronized because a reconnect can briefly leave two connections publishing.
   */
  synchronized void publish(Bar bar) {
    Sinks.EmitResult result = sink.tryEmitNext(bar);
    switch (result) {
      case OK -> {}
      case FAIL_ZERO_SUBSCRIBER -> log.debug("No subscriber for live bar {}", bar);
      default -> log.warn("Live bar {} not delivered: {}", bar, result);
    }
  }
}
