package com.omanfinance.marketingestion.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClockTest {

  private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void parsesOpenMarketWithNewYorkOffset() throws Exception {
    String json =
        """
        {"timestamp":"2026-11-04T10:15:30.123456789-05:00","is_open":true,
         "next_open":"2026-11-05T09:30:00-05:00","next_close":"2026-11-04T16:00:00-05:00"}
        """;

    Clock clock = mapper.readValue(json, Clock.class);

    assertThat(clock)
        .isEqualTo(
            new Clock(
                Instant.parse("2026-11-04T15:15:30.123456789Z"),
                true,
                Instant.parse("2026-11-05T14:30:00Z"),
                Instant.parse("2026-11-04T21:00:00Z")));
  }

  @Test
  void parsesClosedMarket() throws Exception {
    String json =
        """
        {"timestamp":"2026-11-07T12:00:00-05:00","is_open":false,
         "next_open":"2026-11-09T09:30:00-05:00","next_close":"2026-11-09T16:00:00-05:00"}
        """;

    Clock clock = mapper.readValue(json, Clock.class);

    assertThat(clock.isOpen()).isFalse();
    assertThat(clock.nextOpen()).isEqualTo(Instant.parse("2026-11-09T14:30:00Z"));
  }
}
