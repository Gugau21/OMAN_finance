package com.omanfinance.marketingestion.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.omanfinance.marketingestion.api.dto.Clock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Lets a test start the full application without reaching Alpaca {@code GET /v2/clock}. Import
 * {@link Config} to replace the real {@link AlpacaRestClockClient} with one that always returns
 * {@link #OPEN}.
 */
public final class StubAlpacaClock {

  public static final Clock OPEN =
      new Clock(
          Instant.parse("2026-11-09T15:15:30Z"),
          true,
          Instant.parse("2026-11-10T14:30:00Z"),
          Instant.parse("2026-11-09T21:00:00Z"));

  private StubAlpacaClock() {}

  @TestConfiguration
  public static class Config {

    @Bean
    @Primary
    AlpacaRestClockClient stubAlpacaRestClockClient() {
      AlpacaRestClockClient client = mock(AlpacaRestClockClient.class);
      when(client.fetch()).thenReturn(OPEN);
      return client;
    }
  }
}
