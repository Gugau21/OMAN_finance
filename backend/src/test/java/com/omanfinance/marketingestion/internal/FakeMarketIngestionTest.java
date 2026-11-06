package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import com.omanfinance.marketingestion.api.dto.Clock;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.Disposable;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

class FakeMarketIngestionTest {

  private static final Instant NOW = Instant.parse("2026-11-05T15:42:20Z");

  private final VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
  private final InstantSource time = () -> NOW.plusMillis(scheduler.now(TimeUnit.MILLISECONDS));
  private final FakeMarketIngestion fake = new FakeMarketIngestion(time, scheduler);

  @AfterEach
  void disposeScheduler() {
    scheduler.dispose();
  }

  @Test
  void returnsEveryCompletedMinuteInRangeForEachSymbol() {
    BarsResponse response =
        fake.getBars(
            BarsRequest.of(
                List.of("AAPL", "MSFT"),
                BarsRequest.ONE_MINUTE,
                Instant.parse("2026-11-05T15:30:00Z"),
                Instant.parse("2026-11-05T15:34:00Z")));

    assertThat(response.nextPageToken()).isNull();
    assertThat(response.bars()).containsOnlyKeys("AAPL", "MSFT");
    assertThat(response.barsFor("AAPL"))
        .extracting(Bar::timestamp)
        .containsExactly(
            Instant.parse("2026-11-05T15:30:00Z"),
            Instant.parse("2026-11-05T15:31:00Z"),
            Instant.parse("2026-11-05T15:32:00Z"),
            Instant.parse("2026-11-05T15:33:00Z"),
            Instant.parse("2026-11-05T15:34:00Z"));
    assertThat(response.barsFor("MSFT")).extracting(Bar::symbol).containsOnly("MSFT");
  }

  @Test
  void roundsStartUpToTheNextMinute() {
    BarsResponse response =
        fake.getBars(
            BarsRequest.of(
                List.of("AAPL"),
                BarsRequest.ONE_MINUTE,
                Instant.parse("2026-11-05T15:30:01Z"),
                Instant.parse("2026-11-05T15:31:59Z")));

    assertThat(response.barsFor("AAPL"))
        .extracting(Bar::timestamp)
        .containsExactly(Instant.parse("2026-11-05T15:31:00Z"));
  }

  @Test
  void stopsAtTheLastCompletedMinute() {
    BarsResponse response =
        fake.getBars(
            BarsRequest.of(
                List.of("AAPL"),
                BarsRequest.ONE_MINUTE,
                Instant.parse("2026-11-05T15:39:00Z"),
                Instant.parse("2026-11-05T18:00:00Z")));

    assertThat(response.barsFor("AAPL"))
        .extracting(Bar::timestamp)
        .containsExactly(
            Instant.parse("2026-11-05T15:39:00Z"),
            Instant.parse("2026-11-05T15:40:00Z"),
            Instant.parse("2026-11-05T15:41:00Z"));
  }

  @Test
  void defaultsToTheStartOfTheCurrentDayUntilNow() {
    BarsResponse response =
        fake.getBars(BarsRequest.of(List.of("KO"), BarsRequest.ONE_MINUTE, null, null));

    List<Bar> bars = response.barsFor("KO");
    assertThat(bars.getFirst().timestamp()).isEqualTo(Instant.parse("2026-11-05T00:00:00Z"));
    assertThat(bars.getLast().timestamp()).isEqualTo(Instant.parse("2026-11-05T15:41:00Z"));
    assertThat(bars).hasSize(15 * 60 + 42);
  }

  @Test
  void sortsDescendingWhenAsked() {
    BarsRequest request =
        new BarsRequest(
            List.of("AAPL"),
            BarsRequest.ONE_MINUTE,
            Instant.parse("2026-11-05T15:30:00Z"),
            Instant.parse("2026-11-05T15:32:00Z"),
            null,
            BarsRequest.Sort.DESC,
            null,
            null,
            null,
            null);

    assertThat(fake.getBars(request).barsFor("AAPL"))
        .extracting(Bar::timestamp)
        .containsExactly(
            Instant.parse("2026-11-05T15:32:00Z"),
            Instant.parse("2026-11-05T15:31:00Z"),
            Instant.parse("2026-11-05T15:30:00Z"));
  }

  @Test
  void leavesOutSymbolsWithNoBarsInRange() {
    BarsResponse response =
        fake.getBars(
            BarsRequest.of(
                List.of("AAPL"),
                BarsRequest.ONE_MINUTE,
                Instant.parse("2026-11-05T16:00:00Z"),
                Instant.parse("2026-11-05T17:00:00Z")));

    assertThat(response.bars()).isEmpty();
  }

  @Test
  void rejectsTimeframesOtherThanOneMinute() {
    BarsRequest request = BarsRequest.of(List.of("AAPL"), "1Hour", null, null);

    assertThatThrownBy(() -> fake.getBars(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1Hour");
  }

  @Test
  void barsAreConsistentAndOpenAtThePreviousClose() {
    List<Bar> bars =
        fake
            .getBars(
                BarsRequest.of(
                    FakeMarketIngestion.SYMBOLS,
                    BarsRequest.ONE_MINUTE,
                    Instant.parse("2026-11-05T12:00:00Z"),
                    Instant.parse("2026-11-05T15:00:00Z")))
            .bars()
            .values()
            .stream()
            .flatMap(List::stream)
            .toList();

    assertThat(bars)
        .allSatisfy(
            bar -> {
              assertThat(bar.low()).isPositive();
              assertThat(bar.low()).isLessThanOrEqualTo(bar.open().min(bar.close()));
              assertThat(bar.high()).isGreaterThanOrEqualTo(bar.open().max(bar.close()));
              assertThat(bar.vwap()).isBetween(bar.low(), bar.high());
              assertThat(bar.volume()).isPositive();
              assertThat(bar.tradeCount()).isPositive();
            });
    for (int i = 1; i < bars.size(); i++) {
      if (bars.get(i).symbol().equals(bars.get(i - 1).symbol())) {
        assertThat(bars.get(i).open()).isEqualTo(bars.get(i - 1).close());
      }
    }
  }

  @Test
  void streamsOneBarPerSymbolWhenEachMinuteEnds() {
    int symbols = FakeMarketIngestion.SYMBOLS.size();

    StepVerifier.withVirtualTime(fake::streamBars, () -> scheduler, Long.MAX_VALUE)
        .expectSubscription()
        .expectNoEvent(Duration.ofSeconds(39))
        .thenAwait(Duration.ofSeconds(1))
        .expectNextCount(symbols)
        .thenAwait(Duration.ofMinutes(1))
        .expectNextCount(symbols)
        .thenCancel()
        .verify();
  }

  @Test
  void liveBarsEqualHistoricalBarsForTheSameMinute() {
    List<Bar> live = new ArrayList<>();
    Disposable subscription = fake.streamBars().subscribe(live::add);
    scheduler.advanceTimeBy(Duration.ofMinutes(2));
    subscription.dispose();

    BarsResponse historical =
        fake.getBars(
            BarsRequest.of(
                FakeMarketIngestion.SYMBOLS,
                BarsRequest.ONE_MINUTE,
                Instant.parse("2026-11-05T15:42:00Z"),
                null));

    assertThat(live).hasSize(2 * FakeMarketIngestion.SYMBOLS.size());
    assertThat(live)
        .containsExactlyInAnyOrderElementsOf(
            historical.bars().values().stream().flatMap(List::stream).toList());
  }

  @Test
  void reportsAnOpenMarket() {
    Clock clock = fake.getMarketStatus();
    assertThat(clock.isOpen()).isTrue();
    assertThat(clock.timestamp()).isEqualTo(NOW);
    assertThat(clock.nextClose()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
  }

  @Test
  void streamsTheCurrentStatusToEachSubscriberWithoutCompleting() {
    StepVerifier.create(fake.streamMarketStatus())
        .assertNext(clock -> assertThat(clock.isOpen()).isTrue())
        .expectNoEvent(Duration.ofMillis(50))
        .thenCancel()
        .verify();
  }

  @Test
  void tracksAFixedUnmodifiableStockListWithNames() {
    List<Stock> stocks = fake.getTrackedStocks();

    assertThat(stocks).isNotEmpty();
    assertThat(stocks).extracting(Stock::symbol).doesNotHaveDuplicates();
    assertThat(stocks).allSatisfy(stock -> assertThat(stock.name()).isNotBlank());
    assertThatThrownBy(() -> stocks.add(new Stock("TSLA", "Tesla, Inc.")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void addAndRemoveSymbolAreNotImplemented() {
    assertThatThrownBy(() -> fake.addSymbol("TSLA"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> fake.removeSymbol("AAPL"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void isOnlyABeanUnderTheFakeProfile() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(FakeMarketIngestion.class);

    runner
        .withPropertyValues("spring.profiles.active=fake")
        .run(context -> assertThat(context).hasSingleBean(MarketIngestion.class));
    runner.run(context -> assertThat(context).doesNotHaveBean(MarketIngestion.class));
  }
}
