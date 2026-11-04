package com.omanfinance.marketingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BarsResponseTest {

  private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void parsesAlpacaBodyAndSetsSymbolFromMapKey() throws Exception {
    String json =
        """
        {"bars":{
           "AAPL":[{"t":"2026-11-04T14:30:00Z","o":222.1,"h":222.55,"l":221.9,"c":222.3,
                    "v":51234,"n":412,"vw":222.2137},
                   {"t":"2026-11-04T14:31:00Z","o":222.3,"h":222.4,"l":222.0,"c":222.1,
                    "v":40211,"n":350,"vw":222.19}],
           "MSFT":[{"t":"2026-11-04T14:30:00Z","o":410.0,"h":410.5,"l":409.8,"c":410.2,
                    "v":30000,"n":250,"vw":410.11}]},
         "next_page_token":"QUFQTHxNfDIwMjY="}
        """;

    BarsResponse response = mapper.readValue(json, BarsResponse.class);

    assertThat(response.bars()).containsOnlyKeys("AAPL", "MSFT");
    assertThat(response.barsFor("AAPL"))
        .extracting(Bar::symbol, Bar::timestamp)
        .containsExactly(
            tuple("AAPL", Instant.parse("2026-11-04T14:30:00Z")),
            tuple("AAPL", Instant.parse("2026-11-04T14:31:00Z")));
    assertThat(response.barsFor("MSFT").get(0).symbol()).isEqualTo("MSFT");
    assertThat(response.barsFor("MSFT").get(0).close()).isEqualTo(new BigDecimal("410.2"));
    assertThat(response.nextPageToken()).isEqualTo("QUFQTHxNfDIwMjY=");
    assertThat(response.hasNextPage()).isTrue();
  }

  @Test
  void lastPageHasNoToken() throws Exception {
    BarsResponse response =
        mapper.readValue("{\"bars\":{},\"next_page_token\":null}", BarsResponse.class);

    assertThat(response.bars()).isEmpty();
    assertThat(response.barsFor("AAPL")).isEmpty();
    assertThat(response.hasNextPage()).isFalse();
  }

  @Test
  void treatsNullBarsAsEmpty() throws Exception {
    BarsResponse response = mapper.readValue("{\"bars\":null}", BarsResponse.class);

    assertThat(response.bars()).isEmpty();
    assertThat(response.hasNextPage()).isFalse();
  }

  @Test
  void isImmutable() {
    BarsResponse response = new BarsResponse(Map.of("AAPL", List.of()), null);

    assertThatThrownBy(() -> response.bars().put("MSFT", List.of()))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
