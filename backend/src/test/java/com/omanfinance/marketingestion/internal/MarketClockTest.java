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
import reactor.test.scheduler.VirtualTimeScheduler;

class MarketClockTest {

  /** Monday during trading hours: closes at 16:00 ET. */
  private static final Clock OPEN =
      new Clock(
          Instant.parse("2026-11-09T15:15:30Z"),
          true,
          Instant.parse("2026-11-10T14:30:00Z"),
          Instant.parse("2026-11-09T21:00:00Z"));

  /** Monday, just after the close: opens Tuesday at 09:30 ET. */
  private static final Clock CLOSED =
      new Clock(
          Instant.parse("2026-11-09T21:00:05Z"),
          false,
          Instant.parse("2026-11-10T14:30:00Z"),
          Instant.parse("2026-11-10T21:00:00Z"));

  /** Tuesday, just after the open. */
  private static final Clock OPEN_NEXT_DAY =
      new Clock(
          Instant.parse("2026-11-10T14:30:05Z"),
          true,
          Instant.parse("2026-11-11T14:30:00Z"),
          Instant.parse("2026-11-10T21:00:00Z"));

  private final AlpacaClockClient client = mock(AlpacaClockClient.class);
  private final List<Duration> waits = new ArrayList<>();
  private final VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();

  @Test
  void holdsTheStatusFetchedAtStartup() {
    when(client.fetch()).thenReturn(OPEN);

    MarketClock clock = new MarketClock(client, waits::add, scheduler);

    assertThat(clock.current()).isEqualTo(OPEN);
    assertThat(waits).isEmpty();
  }

  @Test
  void doesNotAskAlpacaAgainWhenReadingTheStatus() {
    when(client.fetch()).thenReturn(OPEN);
    MarketClock clock = new MarketClock(client, waits::add, scheduler);

    clock.current();
    clock.current();

    verify(client, times(1)).fetch();
  }

  @Test
  void retriesWithGrowingWaitsWhenAlpacaIsUnavailableAtStartup() {
    when(client.fetch()).thenThrow(unavailable()).thenThrow(unavailable()).thenReturn(OPEN);

    MarketClock clock = new MarketClock(client, waits::add, scheduler);

    assertThat(clock.current()).isEqualTo(OPEN);
    assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
  }

  @Test
  void stopsStartupWhenAlpacaStaysUnavailable() {
    when(client.fetch()).thenThrow(unavailable());

    assertThatThrownBy(() -> new MarketClock(client, waits::add, scheduler))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GET /v2/clock")
        .hasMessageContaining("3 attempts")
        .hasCauseInstanceOf(AlpacaUnavailableException.class);
    verify(client, times(MarketClock.STARTUP_ATTEMPTS)).fetch();
  }

  @Test
  void stopsStartupAtOnceWhenAlpacaRejectsTheKeys() {
    when(client.fetch()).thenThrow(new AlpacaConfigException("rejected"));

    assertThatThrownBy(() -> new MarketClock(client, waits::add, scheduler))
        .isInstanceOf(AlpacaConfigException.class);
    verify(client, times(1)).fetch();
    assertThat(waits).isEmpty();
  }

  @Test
  void refreshesJustAfterTheCloseAndThenJustAfterTheOpen() {
    when(client.fetch()).thenReturn(OPEN, CLOSED, OPEN_NEXT_DAY);
    MarketClock clock = new MarketClock(client, waits::add, scheduler);
    Duration untilClose = Duration.between(OPEN.timestamp(), OPEN.nextClose());

    advance(untilClose.plus(MarketClock.BOUNDARY_MARGIN).minusMillis(1));
    assertThat(clock.current()).isEqualTo(OPEN);

    advance(Duration.ofMillis(1));
    assertThat(clock.current()).isEqualTo(CLOSED);

    advance(
        Duration.between(CLOSED.timestamp(), CLOSED.nextOpen()).plus(MarketClock.BOUNDARY_MARGIN));
    assertThat(clock.current()).isEqualTo(OPEN_NEXT_DAY);
    verify(client, times(3)).fetch();
  }

  @Test
  void takesTheEarlyCloseFromAlpaca() {
    // Fri 27 Nov 2026, the day after Thanksgiving: the market closes at 13:00 ET.
    Clock earlyCloseDay =
        new Clock(
            Instant.parse("2026-11-27T15:00:00Z"),
            true,
            Instant.parse("2026-11-30T14:30:00Z"),
            Instant.parse("2026-11-27T18:00:00Z"));

    assertThat(MarketClock.waitBeforeNextRefresh(earlyCloseDay))
        .isEqualTo(Duration.ofHours(3).plus(MarketClock.BOUNDARY_MARGIN));
  }

  @Test
  void asksAgainSoonWhenAlpacaStillReportsTheOldStateAfterTheBoundary() {
    Clock stillOpenAfterClose =
        new Clock(
            Instant.parse("2026-11-09T21:00:05Z"),
            true,
            Instant.parse("2026-11-10T14:30:00Z"),
            Instant.parse("2026-11-09T21:00:00Z"));

    assertThat(MarketClock.waitBeforeNextRefresh(stillOpenAfterClose))
        .isEqualTo(MarketClock.MIN_REFRESH_WAIT);
  }

  @Test
  void keepsTheOldStatusWhenARefreshFails() {
    when(client.fetch()).thenReturn(OPEN).thenThrow(unavailable());
    MarketClock clock = new MarketClock(client, waits::add, scheduler);

    advance(Duration.ofDays(1));

    assertThat(clock.current()).isEqualTo(OPEN);
    verify(client, times(2)).fetch();
  }

  @Test
  void stopsRefreshingWhenStopped() {
    when(client.fetch()).thenReturn(OPEN, CLOSED);
    MarketClock clock = new MarketClock(client, waits::add, scheduler);

    clock.stop();
    advance(Duration.ofDays(1));

    assertThat(clock.current()).isEqualTo(OPEN);
    verify(client, times(1)).fetch();
  }

  private void advance(Duration duration) {
    scheduler.advanceTimeBy(duration);
  }

  private static AlpacaUnavailableException unavailable() {
    return new AlpacaUnavailableException("Alpaca answered 503 on GET /v2/clock");
  }
}
