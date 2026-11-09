package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.omanfinance.marketingestion.api.dto.Stock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Runs the Flyway migrations against the real PostgreSQL database, then lets Hibernate validate the
 * schema against {@link StockEntity}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockRepositoryTest {

  private static final int TRACKED_STOCK_COUNT = 30;

  @Autowired private StockRepository repository;

  @Test
  void seedsThirtyStocksWithNames() {
    List<Stock> stocks =
        repository.findAllByOrderByIdAsc().stream().map(StockEntity::toStock).toList();

    assertThat(stocks).hasSize(TRACKED_STOCK_COUNT);
    assertThat(stocks).extracting(Stock::symbol).doesNotHaveDuplicates();
    assertThat(stocks).first().isEqualTo(new Stock("AAPL", "Apple Inc."));
  }
}
