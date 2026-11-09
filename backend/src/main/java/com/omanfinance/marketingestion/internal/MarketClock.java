package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.AlpacaConfigException;
import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import com.omanfinance.marketingestion.api.dto.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Holds the market status from Alpaca {@code GET /v2/clock} in memory.
 *
 * <p>The first status is fetched while the bean is created, so it is there before the web server
 * starts and before any other module can ask for it. If Alpaca cannot be reached after {@link
 * #STARTUP_ATTEMPTS} attempts, or rejects the API keys, the application does not start.
 */
@Component
@Profile("!fake")
class MarketClock {

  /** How many times to ask for the first status before giving up. */
  static final int STARTUP_ATTEMPTS = 3;

  /** The wait before the second attempt; it doubles before each one after that. */
  static final Duration FIRST_RETRY_WAIT = Duration.ofSeconds(2);

  private static final Logger log = LoggerFactory.getLogger(MarketClock.class);

  private final AlpacaClockClient client;
  private volatile Clock current;

  @Autowired
  MarketClock(AlpacaClockClient client) {
    this(client, Thread::sleep);
  }

  /**
   * @param sleeper waits between startup attempts; {@code Thread::sleep} outside tests
   * @throws IllegalStateException when Alpaca is still unavailable after {@link #STARTUP_ATTEMPTS}
   *     attempts
   * @throws AlpacaConfigException at once when Alpaca rejects the API keys, since retrying cannot
   *     fix that
   */
  MarketClock(AlpacaClockClient client, AlpacaErrorHandling.Sleeper sleeper) {
    this.client = client;
    this.current = fetchAtStartup(sleeper);
  }

  /** The last market status Alpaca returned. Never null. */
  Clock current() {
    return current;
  }

  private Clock fetchAtStartup(AlpacaErrorHandling.Sleeper sleeper) {
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
                  .formatted(AlpacaClockClient.CLOCK_PATH, STARTUP_ATTEMPTS),
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

  private static void sleep(AlpacaErrorHandling.Sleeper sleeper, Duration wait) {
    try {
      sleeper.sleep(wait);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the market status", e);
    }
  }
}
