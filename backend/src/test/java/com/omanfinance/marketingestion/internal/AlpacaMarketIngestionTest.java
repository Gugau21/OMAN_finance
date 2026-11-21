package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import com.omanfinance.marketingestion.api.dto.Clock;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;

class AlpacaMarketIngestionTest {

  private final ApplicationContextRunner runner = runner(repositoryWithOneStock());

  private final BarHistory barHistory = mock(BarHistory.class);
  private final MarketStatusTracker statusTracker = mock(MarketStatusTracker.class);
  private final LiveBarStream liveBars = new LiveBarStream();
  private final AlpacaStreamClient streamClient = mock(AlpacaStreamClient.class);

  @Test
  void isTheOnlyMarketIngestionWithoutTheFakeProfile() {
    runner.run(
        context ->
            assertThat(context)
                .getBean(MarketIngestion.class)
                .isInstanceOf(AlpacaMarketIngestion.class));
    runner
        .withPropertyValues("spring.profiles.active=prod")
        .run(
            context ->
                assertThat(context)
                    .getBean(MarketIngestion.class)
                    .isInstanceOf(AlpacaMarketIngestion.class));
  }

  @Test
  void stepsAsideForTheFakeProfile() {
    runner
        .withPropertyValues("spring.profiles.active=fake")
        .run(
            context ->
                assertThat(context)
                    .getBean(MarketIngestion.class)
                    .isInstanceOf(FakeMarketIngestion.class));
  }

  @Test
  void tracksTheStocksFromTheTableInIdOrder() {
    StockRepository repository = mock(StockRepository.class);
    when(repository.findAllByOrderByIdAsc())
        .thenReturn(
            List.of(
                new StockEntity("AAPL", "Apple Inc."),
                new StockEntity("MSFT", "Microsoft Corporation")));

    AlpacaMarketIngestion ingestion = ingestion(repository);

    assertThat(ingestion.getTrackedStocks())
        .containsExactly(
            new Stock("AAPL", "Apple Inc."), new Stock("MSFT", "Microsoft Corporation"));
  }

  @Test
  void readsTheTableOnlyOnce() {
    StockRepository repository = repositoryWithOneStock();
    AlpacaMarketIngestion ingestion = ingestion(repository);

    ingestion.getTrackedStocks();
    ingestion.getTrackedStocks();

    verify(repository, times(1)).findAllByOrderByIdAsc();
  }

  @Test
  void trackedStocksAreUnmodifiable() {
    AlpacaMarketIngestion ingestion = ingestion(repositoryWithOneStock());

    assertThatThrownBy(() -> ingestion.getTrackedStocks().add(new Stock("KO", "Coca-Cola")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void doesNotStartWithAnEmptyTable() {
    StockRepository repository = mock(StockRepository.class);
    when(repository.findAllByOrderByIdAsc()).thenReturn(List.of());

    assertThatThrownBy(() -> ingestion(repository))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void getsBarsThroughTheCachingClient() {
    BarsRequest request =
        BarsRequest.of(
            List.of("AAPL"),
            BarsRequest.ONE_MINUTE,
            Instant.parse("2026-11-05T14:30:00Z"),
            Instant.parse("2026-11-05T14:35:00Z"));
    BarsResponse merged = new BarsResponse(Map.of("AAPL", List.of()), null);
    when(barHistory.getBars(request)).thenReturn(merged);
    AlpacaMarketIngestion ingestion = ingestion(repositoryWithOneStock());

    assertThat(ingestion.getBars(request)).isSameAs(merged);
    verify(barHistory).getBars(request);
  }

  @Test
  void getsTheMarketStatusFromTheStatusTracker() {
    Clock status =
        new Clock(
            Instant.parse("2026-11-09T15:15:30Z"),
            true,
            Instant.parse("2026-11-10T14:30:00Z"),
            Instant.parse("2026-11-09T21:00:00Z"));
    when(statusTracker.current()).thenReturn(status);
    AlpacaMarketIngestion ingestion = ingestion(repositoryWithOneStock());

    assertThat(ingestion.getMarketStatus()).isEqualTo(status);
  }

  @Test
  void streamsTheMarketStatusFromTheStatusTracker() {
    Flux<Clock> statuses = Flux.never();
    when(statusTracker.statuses()).thenReturn(statuses);
    AlpacaMarketIngestion ingestion = ingestion(repositoryWithOneStock());

    assertThat(ingestion.streamMarketStatus()).isSameAs(statuses);
  }

  @Test
  void streamsTheBarsPublishedOnTheLiveBarStream() {
    AlpacaMarketIngestion ingestion = ingestion(repositoryWithOneStock());
    List<Bar> received = new CopyOnWriteArrayList<>();
    ingestion.streamBars().subscribe(received::add);
    Bar bar =
        new Bar(
            "AAPL",
            Instant.parse("2026-11-13T15:30:00Z"),
            new BigDecimal("230.10"),
            new BigDecimal("230.50"),
            new BigDecimal("229.90"),
            new BigDecimal("230.20"),
            1200,
            35,
            new BigDecimal("230.21"));

    liveBars.publish(bar);

    assertThat(received).containsExactly(bar);
  }

  @Test
  void doesNotConnectToTheStreamWhenCreated() {
    ingestion(repositoryWithOneStock());

    verifyNoInteractions(streamClient);
  }

  @Test
  void connectsToTheStreamForEveryTrackedSymbolWhenTheApplicationIsReady() {
    StockRepository repository = mock(StockRepository.class);
    when(repository.findAllByOrderByIdAsc())
        .thenReturn(
            List.of(
                new StockEntity("AAPL", "Apple Inc."),
                new StockEntity("MSFT", "Microsoft Corporation")));
    runner(repository)
        .run(
            context -> {
              AlpacaStreamClient client = context.getBean(AlpacaStreamClient.class);
              verifyNoInteractions(client);

              context.publishEvent(
                  new ApplicationReadyEvent(
                      new SpringApplication(),
                      new String[0],
                      context.getSourceApplicationContext(),
                      Duration.ZERO));

              verify(client).connect(List.of("AAPL", "MSFT"));
            });
  }

  private static ApplicationContextRunner runner(StockRepository repository) {
    return new ApplicationContextRunner()
        .withBean(StockRepository.class, () -> repository)
        .withBean(BarHistory.class, () -> mock(BarHistory.class))
        .withBean(MarketStatusTracker.class, () -> mock(MarketStatusTracker.class))
        .withBean(LiveBarStream.class, LiveBarStream::new)
        .withBean(AlpacaStreamClient.class, () -> mock(AlpacaStreamClient.class))
        .withUserConfiguration(AlpacaMarketIngestion.class, FakeMarketIngestion.class);
  }

  private AlpacaMarketIngestion ingestion(StockRepository repository) {
    return new AlpacaMarketIngestion(repository, barHistory, statusTracker, liveBars, streamClient);
  }

  private static StockRepository repositoryWithOneStock() {
    StockRepository repository = mock(StockRepository.class);
    when(repository.findAllByOrderByIdAsc())
        .thenReturn(List.of(new StockEntity("AAPL", "Apple Inc.")));
    return repository;
  }
}
