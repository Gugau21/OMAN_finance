package com.omanfinance.shared.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The time source for the whole backend. */
@Configuration
class ClockConfig {

  /**
   * The current time in UTC. Inject it instead of calling {@code Instant.now()}, so tests can set
   * the time.
   */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
