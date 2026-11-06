package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AlpacaMarketIngestionTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(StockRepository.class, AlpacaMarketIngestionTest::repositoryWithOneStock)
          .withUserConfiguration(AlpacaMarketIngestion.class, FakeMarketIngestion.class);

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

    AlpacaMarketIngestion ingestion = new AlpacaMarketIngestion(repository);

    assertThat(ingestion.getTrackedStocks())
        .containsExactly(
            new Stock("AAPL", "Apple Inc."), new Stock("MSFT", "Microsoft Corporation"));
  }

  @Test
  void readsTheTableOnlyOnce() {
    StockRepository repository = repositoryWithOneStock();
    AlpacaMarketIngestion ingestion = new AlpacaMarketIngestion(repository);

    ingestion.getTrackedStocks();
    ingestion.getTrackedStocks();

    verify(repository, times(1)).findAllByOrderByIdAsc();
  }

  @Test
  void trackedStocksAreUnmodifiable() {
    AlpacaMarketIngestion ingestion = new AlpacaMarketIngestion(repositoryWithOneStock());

    assertThatThrownBy(() -> ingestion.getTrackedStocks().add(new Stock("KO", "Coca-Cola")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void doesNotStartWithAnEmptyTable() {
    StockRepository repository = mock(StockRepository.class);
    when(repository.findAllByOrderByIdAsc()).thenReturn(List.of());

    assertThatThrownBy(() -> new AlpacaMarketIngestion(repository))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty");
  }

  private static StockRepository repositoryWithOneStock() {
    StockRepository repository = mock(StockRepository.class);
    when(repository.findAllByOrderByIdAsc())
        .thenReturn(List.of(new StockEntity("AAPL", "Apple Inc.")));
    return repository;
  }
}
