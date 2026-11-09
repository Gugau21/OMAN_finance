package com.omanfinance.marketfeed.web;

import static com.omanfinance.marketfeed.web.ApiContract.matchesContract;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MarketController.class)
class MarketControllerTest {

  private static final Clock CLOSED =
      new Clock(
          Instant.parse("2026-11-09T22:15:30Z"),
          false,
          Instant.parse("2026-11-10T14:30:00Z"),
          Instant.parse("2026-11-10T21:00:00Z"));

  @Autowired private MockMvc mvc;

  @MockitoBean private MarketIngestion marketIngestion;

  @Test
  void returnsTheMarketStatusInTheContractShape() throws Exception {
    when(marketIngestion.getMarketStatus()).thenReturn(CLOSED);

    mvc.perform(get("/api/market/status"))
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.timestamp").value("2026-11-09T22:15:30Z"))
        .andExpect(jsonPath("$.isOpen").value(false))
        .andExpect(jsonPath("$.nextOpen").value("2026-11-10T14:30:00Z"))
        .andExpect(jsonPath("$.nextClose").value("2026-11-10T21:00:00Z"));
  }
}
