package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

class MarketStatusTrackerTest {

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

  /** Longer than any test runs, so only the tests about the safety net see it. */
  private static final Duration NO_SAFETY_NET = Duration.ofDays(7);

  private final AlpacaClockClient client = mock(AlpacaClockClient.class);
  private final List<Duration> waits = new ArrayList<>();
  private final VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();

  @Test
  void holdsTheStatusFetchedAtStartup() {
    when(client.fetch()).thenReturn(OPEN);

    MarketStatusTracker clock = start();

    assertThat(clock.current()).isEqualTo(OPEN);
    assertThat(waits).isEmpty();
  }

  @Test
  void doesNotAskAlpacaAgainWhenReadingTheStatus() {
    when(client.fetch()).thenReturn(OPEN);
    MarketStatusTracker clock = start();

    clock.current();
    clock.current();

    verify(client, times(1)).fetch();
  }

  @Test
  void retriesWithGrowingWaitsWhenAlpacaIsUnavailableAtStartup() {
    when(client.fetch()).thenThrow(unavailable()).thenThrow(unavailable()).thenReturn(OPEN);

    MarketStatusTracker clock = start();

    assertThat(clock.current()).isEqualTo(OPEN);
    assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
  }

  @Test
  void stopsStartupWhenAlpacaStaysUnavailable() {
    when(client.fetch()).thenThrow(unavailable());

    assertThatThrownBy(() -> start())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GET /v2/clock")
        .hasMessageContaining("3 attempts")
        .hasCauseInstanceOf(AlpacaUnavailableException.class);
    verify(client, times(MarketStatusTracker.STARTUP_ATTEMPTS)).fetch();
  }

  @Test
  void stopsStartupAtOnceWhenAlpacaRejectsTheKeys() {
    when(client.fetch()).thenThrow(new AlpacaConfigException("rejected"));

    assertThatThrownBy(() -> start()).isInstanceOf(AlpacaConfigException.class);
    verify(client, times(1)).fetch();
    assertThat(waits).isEmpty();
  }

  @Test
  void refreshesJustAfterTheCloseAndThenJustAfterTheOpen() {
    when(client.fetch()).thenReturn(OPEN, CLOSED, OPEN_NEXT_DAY);
    MarketStatusTracker clock = start();
    Duration untilClose = Duration.between(OPEN.timestamp(), OPEN.nextClose());

    advance(untilClose.plus(MarketStatusTracker.BOUNDARY_MARGIN).minusMillis(1));
    assertThat(clock.current()).isEqualTo(OPEN);

    advance(Duration.ofMillis(1));
    assertThat(clock.current()).isEqualTo(CLOSED);

    advance(
        Duration.between(CLOSED.timestamp(), CLOSED.nextOpen())
            .plus(MarketStatusTracker.BOUNDARY_MARGIN));
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

    assertThat(MarketStatusTracker.waitBeforeNextRefresh(earlyCloseDay))
        .isEqualTo(Duration.ofHours(3).plus(MarketStatusTracker.BOUNDARY_MARGIN));
  }

  @Test
  void asksAgainSoonWhenAlpacaStillReportsTheOldStateAfterTheBoundary() {
    Clock stillOpenAfterClose =
        new Clock(
            Instant.parse("2026-11-09T21:00:05Z"),
            true,
            Instant.parse("2026-11-10T14:30:00Z"),
            Instant.parse("2026-11-09T21:00:00Z"));

    assertThat(MarketStatusTracker.waitBeforeNextRefresh(stillOpenAfterClose))
        .isEqualTo(MarketStatusTracker.MIN_REFRESH_WAIT);
  }

  @Test
  void keepsTheOldStatusWhenARefreshFails() {
    when(client.fetch()).thenReturn(OPEN).thenThrow(unavailable());
    MarketStatusTracker clock = start();

    advance(Duration.ofDays(1));

    assertThat(clock.current()).isEqualTo(OPEN);
    verify(client, times(2)).fetch();
  }

  @Test
  void stopsRefreshingWhenStopped() {
    when(client.fetch()).thenReturn(OPEN, CLOSED);
    MarketStatusTracker clock = start();

    clock.stop();
    advance(Duration.ofDays(1));

    assertThat(clock.current()).isEqualTo(OPEN);
    verify(client, times(1)).fetch();
  }

  @Test
  void callsAlpacaOncePerResponseFromStartupThroughCloseAndOpenWithTwoSafetyNetRefreshes() {
    // Safety net every 8 h from 15:15:30Z: 23:15:30Z and 07:15:30Z, both while closed.
    when(client.fetch())
        .thenReturn(
            OPEN,
            CLOSED,
            closedAt("2026-11-09T23:15:30Z"),
            closedAt("2026-11-10T07:15:30Z"),
            OPEN_NEXT_DAY);
    MarketStatusTracker clock = startWithRefreshEvery(Duration.ofHours(8));

    advanceTo("2026-11-10T14:31:00Z");

    // Each safety-net refresh replaces the pending open refresh; were they added instead, three
    // would run at 14:30:05Z.
    verify(client, times(5)).fetch();
    assertThat(clock.current()).isEqualTo(OPEN_NEXT_DAY);
  }

  @Test
  void safetyNetRefreshesEveryInterval() {
    when(client.fetch()).thenReturn(OPEN);
    startWithRefreshEvery(Duration.ofMinutes(15));

    advance(Duration.ofHours(1));

    verify(client, times(1 + 4)).fetch();
  }

  @Test
  void safetyNetCorrectsTheStatusAfterAFailedCallAtTheClose() {
    when(client.fetch())
        .thenReturn(OPEN)
        .thenThrow(unavailable())
        .thenReturn(
            closedAt("2026-11-09T23:15:30Z"), closedAt("2026-11-10T07:15:30Z"), OPEN_NEXT_DAY);
    MarketStatusTracker clock = startWithRefreshEvery(Duration.ofHours(8));

    advanceTo("2026-11-09T21:00:05Z");
    assertThat(clock.current()).isEqualTo(OPEN);

    advanceTo("2026-11-09T23:15:30Z");
    assertThat(clock.current().isOpen()).isFalse();

    advanceTo("2026-11-10T14:30:05Z");
    assertThat(clock.current()).isEqualTo(OPEN_NEXT_DAY);
    // startup, the failed call at the close, safety nets at 23:15:30Z and 07:15:30Z, the open
    verify(client, times(5)).fetch();
  }

  @Test
  void rejectsARefreshIntervalThatIsNotPositive() {
    assertThatThrownBy(() -> startWithRefreshEvery(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("alpaca.clock-refresh-interval");
    verifyNoInteractions(client);
  }

  private MarketStatusTracker start() {
    return startWithRefreshEvery(NO_SAFETY_NET);
  }

  private MarketStatusTracker startWithRefreshEvery(Duration refreshInterval) {
    return new MarketStatusTracker(client, waits::add, scheduler, refreshInterval);
  }

  /** Closed on Monday evening or Tuesday morning, as Alpaca reports it at {@code timestamp}. */
  private static Clock closedAt(String timestamp) {
    return new Clock(
        Instant.parse(timestamp),
        false,
        Instant.parse("2026-11-10T14:30:00Z"),
        Instant.parse("2026-11-10T21:00:00Z"));
  }

  /** Moves virtual time to {@code instant}, counting from the startup status {@link #OPEN}. */
  private void advanceTo(String instant) {
    Duration sinceStartup = Duration.between(OPEN.timestamp(), Instant.parse(instant));
    scheduler.advanceTimeTo(Instant.ofEpochMilli(sinceStartup.toMillis()));
  }

  private void advance(Duration duration) {
    scheduler.advanceTimeBy(duration);
  }

  private static AlpacaUnavailableException unavailable() {
    return new AlpacaUnavailableException("Alpaca answered 503 on GET /v2/clock");
  }
}
