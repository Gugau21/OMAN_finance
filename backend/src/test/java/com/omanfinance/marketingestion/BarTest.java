package com.omanfinance.marketingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BarTest {

  private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void parsesLiveWebSocketBarUnchanged() throws Exception {
    String json =
        """
        {"T":"b","S":"AAPL","o":222.1,"h":222.55,"l":221.9,"c":222.3,
         "v":51234,"t":"2026-11-04T14:30:00Z","n":412,"vw":222.2137}
        """;

    Bar bar = mapper.readValue(json, Bar.class);

    assertThat(bar)
        .isEqualTo(
            new Bar(
                "AAPL",
                Instant.parse("2026-11-04T14:30:00Z"),
                new BigDecimal("222.1"),
                new BigDecimal("222.55"),
                new BigDecimal("221.9"),
                new BigDecimal("222.3"),
                51234,
                412,
                new BigDecimal("222.2137")));
  }

  @Test
  void restBarGetsSymbolFromMapKey() throws Exception {
    String json =
        """
        {"t":"2026-11-04T14:30:00Z","o":222.1,"h":222.55,"l":221.9,"c":222.3,
         "v":51234,"n":412,"vw":222.2137}
        """;

    Bar bar = mapper.readValue(json, Bar.class);

    assertThat(bar.symbol()).isNull();
    assertThat(bar.withSymbol("AAPL").symbol()).isEqualTo("AAPL");
    assertThat(bar.withSymbol("AAPL").close()).isEqualTo(new BigDecimal("222.3"));
  }
}
