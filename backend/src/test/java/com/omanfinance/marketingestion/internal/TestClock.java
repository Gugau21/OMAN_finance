package com.omanfinance.marketingestion.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** A clock that stays where a test puts it. Import {@link Config} to get it as the Clock bean. */
final class TestClock extends Clock {

  private Instant now = Instant.EPOCH;

  void set(Instant now) {
    this.now = now;
  }

  @Override
  public Instant instant() {
    return now;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("TestClock is always UTC");
  }

  @TestConfiguration
  static class Config {

    @Bean
    TestClock testClock() {
      return new TestClock();
    }
  }
}
