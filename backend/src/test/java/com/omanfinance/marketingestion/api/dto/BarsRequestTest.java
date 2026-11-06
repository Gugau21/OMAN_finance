package com.omanfinance.marketingestion.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BarsRequestTest {

  private static final Instant START = Instant.parse("2026-11-04T14:30:00Z");
  private static final Instant END = Instant.parse("2026-11-04T15:30:00Z");

  @Test
  void fillsFixedDefaultsAndLeavesTimeDefaultsToAlpaca() {
    BarsRequest request = BarsRequest.of(List.of("AAPL"), BarsRequest.ONE_MINUTE, null, null);

    assertThat(request.limit()).isEqualTo(1000);
    assertThat(request.sort()).isEqualTo(BarsRequest.Sort.ASC);
    assertThat(request.adjustment()).isEqualTo(BarsRequest.Adjustment.RAW);
    assertThat(request.currency()).isEqualTo("USD");
    assertThat(request.start()).isNull();
    assertThat(request.end()).isNull();
    assertThat(request.asof()).isNull();
    assertThat(request.pageToken()).isNull();
  }

  @Test
  void keepsExplicitValues() {
    BarsRequest request =
        new BarsRequest(
            List.of("AAPL", "MSFT"),
            "5Min",
            START,
            END,
            60,
            BarsRequest.Sort.DESC,
            BarsRequest.Adjustment.SPLIT,
            null,
            "EUR",
            "tok");

    assertThat(request.limit()).isEqualTo(60);
    assertThat(request.sort()).isEqualTo(BarsRequest.Sort.DESC);
    assertThat(request.adjustment()).isEqualTo(BarsRequest.Adjustment.SPLIT);
    assertThat(request.currency()).isEqualTo("EUR");
    assertThat(request.pageToken()).isEqualTo("tok");
  }

  @Test
  void withPageTokenChangesOnlyTheToken() {
    BarsRequest first = BarsRequest.of(List.of("AAPL"), BarsRequest.ONE_MINUTE, START, END);

    BarsRequest next = first.withPageToken("abc");

    assertThat(next.pageToken()).isEqualTo("abc");
    assertThat(next.withPageToken(null)).isEqualTo(first);
  }

  @Test
  void copiesSymbols() {
    List<String> symbols = new ArrayList<>(List.of("AAPL"));
    BarsRequest request = BarsRequest.of(symbols, BarsRequest.ONE_MINUTE, null, null);

    symbols.add("MSFT");

    assertThat(request.symbols()).containsExactly("AAPL");
  }

  @Test
  void rejectsInvalidRequests() {
    assertThatThrownBy(() -> BarsRequest.of(List.of(), BarsRequest.ONE_MINUTE, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BarsRequest.of(null, BarsRequest.ONE_MINUTE, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> BarsRequest.of(Arrays.asList("AAPL", null), BarsRequest.ONE_MINUTE, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BarsRequest.of(List.of("AAPL"), " ", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BarsRequest.of(List.of("AAPL"), BarsRequest.ONE_MINUTE, END, START))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsLimitOutsideAlpacaRange() {
    assertThatThrownBy(() -> withLimit(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> withLimit(10_001)).isInstanceOf(IllegalArgumentException.class);
    assertThat(withLimit(1).limit()).isEqualTo(1);
    assertThat(withLimit(10_000).limit()).isEqualTo(10_000);
  }

  @Test
  void enumsUseAlpacaQueryValues() {
    assertThat(BarsRequest.Sort.ASC.value()).isEqualTo("asc");
    assertThat(BarsRequest.Sort.DESC.value()).isEqualTo("desc");
    assertThat(BarsRequest.Adjustment.SPIN_OFF.value()).isEqualTo("spin-off");
    assertThat(BarsRequest.Adjustment.ALL.value()).isEqualTo("all");
  }

  private static BarsRequest withLimit(int limit) {
    return new BarsRequest(
        List.of("AAPL"), BarsRequest.ONE_MINUTE, null, null, limit, null, null, null, null, null);
  }
}
