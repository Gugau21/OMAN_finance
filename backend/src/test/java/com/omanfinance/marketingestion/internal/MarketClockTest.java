package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.marketingestion.api.AlpacaConfigException;
import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import com.omanfinance.marketingestion.api.dto.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketClockTest {

  private static final Clock OPEN =
      new Clock(
          Instant.parse("2026-11-09T15:15:30Z"),
          true,
          Instant.parse("2026-11-10T14:30:00Z"),
          Instant.parse("2026-11-09T21:00:00Z"));

  private final AlpacaClockClient client = mock(AlpacaClockClient.class);
  private final List<Duration> waits = new ArrayList<>();

  @Test
  void holdsTheStatusFetchedAtStartup() {
    when(client.fetch()).thenReturn(OPEN);

    MarketClock clock = new MarketClock(client, waits::add);

    assertThat(clock.current()).isEqualTo(OPEN);
    assertThat(waits).isEmpty();
  }

  @Test
  void doesNotAskAlpacaAgainWhenReadingTheStatus() {
    when(client.fetch()).thenReturn(OPEN);
    MarketClock clock = new MarketClock(client, waits::add);

    clock.current();
    clock.current();

    verify(client, times(1)).fetch();
  }

  @Test
  void retriesWithGrowingWaitsWhenAlpacaIsUnavailableAtStartup() {
    when(client.fetch()).thenThrow(unavailable()).thenThrow(unavailable()).thenReturn(OPEN);

    MarketClock clock = new MarketClock(client, waits::add);

    assertThat(clock.current()).isEqualTo(OPEN);
    assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
  }

  @Test
  void stopsStartupWhenAlpacaStaysUnavailable() {
    when(client.fetch()).thenThrow(unavailable());

    assertThatThrownBy(() -> new MarketClock(client, waits::add))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GET /v2/clock")
        .hasMessageContaining("3 attempts")
        .hasCauseInstanceOf(AlpacaUnavailableException.class);
    verify(client, times(MarketClock.STARTUP_ATTEMPTS)).fetch();
  }

  @Test
  void stopsStartupAtOnceWhenAlpacaRejectsTheKeys() {
    when(client.fetch()).thenThrow(new AlpacaConfigException("rejected"));

    assertThatThrownBy(() -> new MarketClock(client, waits::add))
        .isInstanceOf(AlpacaConfigException.class);
    verify(client, times(1)).fetch();
    assertThat(waits).isEmpty();
  }

  private static AlpacaUnavailableException unavailable() {
    return new AlpacaUnavailableException("Alpaca answered 503 on GET /v2/clock");
  }
}
