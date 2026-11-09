package com.omanfinance.marketingestion.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class StockTest {

  private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void serializesAsTheContractStock() throws Exception {
    String json = mapper.writeValueAsString(new Stock("AAPL", "Apple Inc."));

    assertThat(json).isEqualTo("{\"symbol\":\"AAPL\",\"name\":\"Apple Inc.\"}");
  }

  @Test
  void rejectsBlankSymbolOrName() {
    assertThatThrownBy(() -> new Stock(null, "Apple Inc."))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Stock(" ", "Apple Inc."))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Stock("AAPL", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Stock("AAPL", "")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSymbolLongerThanTheColumn() {
    assertThat(new Stock("A".repeat(Stock.MAX_SYMBOL_LENGTH), "x").symbol())
        .hasSize(Stock.MAX_SYMBOL_LENGTH);
    assertThatThrownBy(() -> new Stock("A".repeat(Stock.MAX_SYMBOL_LENGTH + 1), "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
