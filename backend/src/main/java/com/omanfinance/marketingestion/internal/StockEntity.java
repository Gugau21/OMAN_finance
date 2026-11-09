package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.dto.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A row of the {@code stock} table, created and seeded by {@code V1__create_stock.sql}. */
@Entity
@Table(name = "stock")
class StockEntity {

  static final int MAX_NAME_LENGTH = 128;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(nullable = false, unique = true, length = Stock.MAX_SYMBOL_LENGTH)
  private String symbol;

  @Column(nullable = false, length = MAX_NAME_LENGTH)
  private String name;

  protected StockEntity() {}

  StockEntity(String symbol, String name) {
    this.symbol = symbol;
    this.name = name;
  }

  Stock toStock() {
    return new Stock(symbol, name);
  }
}
