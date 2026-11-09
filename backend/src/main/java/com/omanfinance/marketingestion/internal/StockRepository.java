package com.omanfinance.marketingestion.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** The {@code stock} table. Only {@code marketingestion} reads it. */
interface StockRepository extends JpaRepository<StockEntity, Integer> {

  /** Every stock, in the order the rows were inserted. */
  List<StockEntity> findAllByOrderByIdAsc();
}
