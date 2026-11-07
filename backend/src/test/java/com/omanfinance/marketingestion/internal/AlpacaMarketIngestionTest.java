package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AlpacaMarketIngestionTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(StockRepository.class, AlpacaMarketIngestionTest::repositoryWithOneStock)
          .withBean(CachingBarsClient.class, () -> mock(CachingBarsClient.class))
          .withUserConfiguration(AlpacaMarketIngestion.class, FakeMarketIngestion.class);

  private final CachingBarsClient barsClient = mock(CachingBarsClient.class);

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

    AlpacaMarketIngestion ingestion = new AlpacaMarketIngestion(repository, barsClient);

    assertThat(ingestion.getTrackedStocks())
        .containsExactly(
            new Stock("AAPL", "Apple Inc."), new Stock("MSFT", "Microsoft Corporation"));
  }

  @Test
  void readsTheTableOnlyOnce() {
    StockRepository repository = repositoryWithOneStock();
    AlpacaMarketIngestion ingestion = new AlpacaMarketIngestion(repository, barsClient);

    ingestion.getTrackedStocks();
    ingestion.getTrackedStocks();

    verify(repository, times(1)).findAllByOrderByIdAsc();
  }

  @Test
  void trackedStocksAreUnmodifiable() {
    AlpacaMarketIngestion ingestion =
        new AlpacaMarketIngestion(repositoryWithOneStock(), barsClient);

    assertThatThrownBy(() -> ingestion.getTrackedStocks().add(new Stock("KO", "Coca-Cola")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void doesNotStartWithAnEmptyTable() {
    StockRepository repository = mock(StockRepository.class);
    when(repository.findAllByOrderByIdAsc()).thenReturn(List.of());

    assertThatThrownBy(() -> new AlpacaMarketIngestion(repository, barsClient))
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
    when(barsClient.getBars(request)).thenReturn(merged);
    AlpacaMarketIngestion ingestion =
        new AlpacaMarketIngestion(repositoryWithOneStock(), barsClient);

    assertThat(ingestion.getBars(request)).isSameAs(merged);
    verify(barsClient).getBars(request);
  }

  private static StockRepository repositoryWithOneStock() {
    StockRepository repository = mock(StockRepository.class);
    when(repository.findAllByOrderByIdAsc())
        .thenReturn(List.of(new StockEntity("AAPL", "Apple Inc.")));
    return repository;
  }
}
