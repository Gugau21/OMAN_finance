package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.AlpacaConfigException;
import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import com.omanfinance.marketingestion.api.dto.Clock;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Holds the market status from Alpaca {@code GET /v2/clock} in memory.
 *
 * <p>The first status is fetched while the bean is created, so it is there before the web server
 * starts and before any other module can ask for it. If Alpaca cannot be reached after {@link
 * #STARTUP_ATTEMPTS} attempts, or rejects the API keys, the application does not start.
 *
 * <p>After each status, the next one is fetched {@link #BOUNDARY_MARGIN} after the next close (when
 * the market is open) or the next open (when it is closed), as Alpaca reported them, so early
 * closes and holidays need no calendar of our own. The wait is measured from the status's own
 * {@code timestamp}, so a skewed local clock does not move it.
 *
 * <p>On top of that, a safety-net refresh runs every {@code alpaca.clock-refresh-interval}. A
 * failed refresh keeps the old status and schedules nothing, so after a failed call at the open or
 * close, the next safety-net refresh corrects the status and schedules the next boundary again.
 * Every successful refresh, safety-net ones included, replaces the pending boundary refresh.
 */
@Component
@Profile("!fake")
class MarketStatusTracker {

  /** How many times to ask for the first status before giving up. */
  static final int STARTUP_ATTEMPTS = 3;

  /** The wait before the second attempt; it doubles before each one after that. */
  static final Duration FIRST_RETRY_WAIT = Duration.ofSeconds(2);

  /**
   * How long after the open or close to call Alpaca. Our timer and Alpaca's {@code timestamp} can
   * be slightly out of step, and the margin keeps the call from landing just before the boundary.
   */
  static final Duration BOUNDARY_MARGIN = Duration.ofSeconds(5);

  /**
   * The shortest wait before the next boundary refresh, for when the boundary Alpaca reports has
   * already passed; without it the refresh would run again at once, over and over.
   */
  static final Duration MIN_REFRESH_WAIT = Duration.ofSeconds(5);

  private static final Logger log = LoggerFactory.getLogger(MarketStatusTracker.class);

  private final AlpacaRestClockClient client;
  private final Scheduler scheduler;
  private volatile Clock current;
  private Disposable boundaryRefresh = Disposables.disposed();

  @Autowired
  MarketStatusTracker(AlpacaRestClockClient client, AlpacaProperties properties) {
    this(
        client,
        Thread::sleep,
        Schedulers.newSingle("market-status", true),
        properties.clockRefreshInterval());
  }

  /**
   * @param sleeper waits between startup attempts; {@code Thread::sleep} outside tests
   * @param scheduler runs the refreshes; disposed by {@link #stop}
   * @param refreshInterval how often the safety-net refresh runs
   * @throws IllegalArgumentException when {@code refreshInterval} is not positive
   * @throws IllegalStateException when Alpaca is still unavailable after {@link #STARTUP_ATTEMPTS}
   *     attempts
   * @throws AlpacaConfigException at once when Alpaca rejects the API keys, since retrying cannot
   *     fix that
   */
  MarketStatusTracker(
      AlpacaRestClockClient client,
      AlpacaRestErrorHandling.Sleeper sleeper,
      Scheduler scheduler,
      Duration refreshInterval) {
    if (refreshInterval == null || refreshInterval.isNegative() || refreshInterval.isZero()) {
      throw new IllegalArgumentException(
          "alpaca.clock-refresh-interval must be positive, was " + refreshInterval);
    }
    this.client = client;
    this.scheduler = scheduler;
    this.current = fetchAtStartup(sleeper);
    synchronized (this) {
      scheduleBoundaryRefresh(current);
    }
    scheduler.schedulePeriodically(
        this::refresh,
        refreshInterval.toMillis(),
        refreshInterval.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  /** The last market status Alpaca returned. Never null. */
  Clock current() {
    return current;
  }

  /** Cancels the pending refreshes when the application shuts down. */
  @PreDestroy
  void stop() {
    scheduler.dispose();
  }

  /**
   * Fetches the status again and replaces the pending boundary refresh with one for the new status.
   * Runs at the open or close and on every safety-net refresh. When the fetch fails, the old status
   * and the pending boundary refresh stay.
   */
  private synchronized void refresh() {
    Clock clock;
    try {
      clock = client.fetch();
    } catch (AlpacaUnavailableException | AlpacaConfigException | RestClientException e) {
      log.warn("Market status refresh failed, keeping {}: {}", current, e.getMessage());
      return;
    }
    current = clock;
    log.info("Market status refreshed: {}", clock);
    scheduleBoundaryRefresh(clock);
  }

  /** Cancels the pending boundary refresh, if any, and schedules one for {@code clock}. */
  private void scheduleBoundaryRefresh(Clock clock) {
    Duration wait = waitBeforeNextRefresh(clock);
    boundaryRefresh.dispose();
    boundaryRefresh = scheduler.schedule(this::refresh, wait.toMillis(), TimeUnit.MILLISECONDS);
    log.debug("Next market status refresh at the open or close in {}", wait);
  }

  /**
   * {@link #BOUNDARY_MARGIN} past the next close when the market is open, or the next open when it
   * is closed, measured from {@code clock.timestamp()}; never less than {@link #MIN_REFRESH_WAIT}.
   */
  static Duration waitBeforeNextRefresh(Clock clock) {
    Instant boundary = clock.isOpen() ? clock.nextClose() : clock.nextOpen();
    Duration wait = Duration.between(clock.timestamp(), boundary).plus(BOUNDARY_MARGIN);
    return wait.compareTo(MIN_REFRESH_WAIT) < 0 ? MIN_REFRESH_WAIT : wait;
  }

  private Clock fetchAtStartup(AlpacaRestErrorHandling.Sleeper sleeper) {
    Duration wait = FIRST_RETRY_WAIT;
    for (int attempt = 1; ; attempt++) {
      try {
        Clock clock = client.fetch();
        log.info("Market status at startup: {}", clock);
        return clock;
      } catch (AlpacaUnavailableException e) {
        if (attempt == STARTUP_ATTEMPTS) {
          throw new IllegalStateException(
              "Could not get the market status from Alpaca GET %s after %d attempts"
                  .formatted(AlpacaRestClockClient.CLOCK_PATH, STARTUP_ATTEMPTS),
              e);
        }
        log.warn(
            "Market status not available at startup (attempt {} of {}), retrying in {}: {}",
            attempt,
            STARTUP_ATTEMPTS,
            wait,
            e.getMessage());
        sleep(sleeper, wait);
        wait = wait.multipliedBy(2);
      }
    }
  }

  private static void sleep(AlpacaRestErrorHandling.Sleeper sleeper, Duration wait) {
    try {
      sleeper.sleep(wait);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the market status", e);
    }
  }
}
