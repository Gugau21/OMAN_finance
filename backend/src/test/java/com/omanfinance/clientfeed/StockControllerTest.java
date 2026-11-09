package com.omanfinance.clientfeed;

import static com.omanfinance.clientfeed.ApiContract.matchesContract;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.omanfinance.marketingestion.api.MarketIngestion;
import com.omanfinance.marketingestion.api.dto.Stock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockController.class)
class StockControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private MarketIngestion marketIngestion;

  @Test
  void returnsTheTrackedStocksInTheContractShapeAndOrder() throws Exception {
    when(marketIngestion.getTrackedStocks())
        .thenReturn(
            List.of(
                new Stock("MSFT", "Microsoft Corporation"),
                new Stock("AAPL", "Apple Inc."),
                new Stock("NVDA", "NVIDIA Corporation")));

    mvc.perform(get("/api/stocks"))
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].symbol").value("MSFT"))
        .andExpect(jsonPath("$[0].name").value("Microsoft Corporation"))
        .andExpect(jsonPath("$[1].symbol").value("AAPL"))
        .andExpect(jsonPath("$[2].symbol").value("NVDA"));
  }

  @Test
  void returnsAnEmptyListWhenNothingIsTracked() throws Exception {
    when(marketIngestion.getTrackedStocks()).thenReturn(List.of());

    mvc.perform(get("/api/stocks"))
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
