package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Runs against the real Redis that {@code spring.data.redis.*} points at. Only touches the keys of
 * {@link #SYMBOL}, so it does not disturb bars cached by a running backend.
 */
@DataRedisTest
@Import(BarRedisRepository.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class BarRedisRepositoryTest {

  private static final String SYMBOL = "TEST_BARS";
  private static final Instant T0 = Instant.parse("2026-11-05T14:30:00Z");
  private static final Instant T1 = T0.plusSeconds(60);
  private static final Instant T2 = T0.plusSeconds(120);
  private static final Instant T3 = T0.plusSeconds(180);

  @Autowired private BarRedisRepository repository;
  @Autowired private StringRedisTemplate redis;
  @Autowired private ObjectMapper json;

  @BeforeEach
  @AfterEach
  void deleteTestKeys() {
    redis.delete(List.of(BarRedisRepository.keyOf(SYMBOL), BarRedisRepository.spanKeyOf(SYMBOL)));
  }

  @Test
  void returnsTheRecordedAlpacaBarsUnchanged() throws IOException {
    BarsResponse recorded =
        json.readValue(
            new ClassPathResource("alpaca/bars-page-1.json").getInputStream(), BarsResponse.class);
    List<Bar> bars = recorded.barsFor("AAPL").stream().map(bar -> bar.withSymbol(SYMBOL)).toList();

    repository.save(SYMBOL, bars, T0, T1);

    // Record equality compares BigDecimal with equals, so 270.5 must not come back as 270.50.
    assertThat(repository.read(SYMBOL, T0, T1)).containsExactlyElementsOf(bars);
  }

  @Test
  void readsOnlyTheBarsInTheRangeWithBothEndsIncluded() {
    repository.save(SYMBOL, List.of(bar(T0, "1"), bar(T1, "2"), bar(T2, "3")), T0, T2);

    assertThat(repository.read(SYMBOL, T1, T2)).extracting(Bar::timestamp).containsExactly(T1, T2);
    assertThat(repository.read(SYMBOL, T1, T1)).extracting(Bar::timestamp).containsExactly(T1);
  }

  @Test
  void returnsBarsOldestFirstWhateverOrderTheyWereSavedIn() {
    repository.save(SYMBOL, List.of(bar(T2, "3"), bar(T0, "1"), bar(T1, "2")), T0, T2);

    assertThat(repository.read(SYMBOL, T0, T2))
        .extracting(Bar::timestamp)
        .containsExactly(T0, T1, T2);
  }

  @Test
  void savingARangeAgainReplacesTheBarsInIt() {
    repository.save(SYMBOL, List.of(bar(T0, "1"), bar(T1, "1")), T0, T1);

    repository.save(SYMBOL, List.of(bar(T1, "2")), T0, T1);

    assertThat(repository.read(SYMBOL, T0, T2)).containsExactly(bar(T1, "2"));
  }

  @Test
  void savingKeepsTheBarsOutsideTheRange() {
    repository.save(SYMBOL, List.of(bar(T0, "1"), bar(T2, "3")), T0, T2);

    repository.save(SYMBOL, List.of(bar(T1, "2")), T1, T1);

    assertThat(repository.read(SYMBOL, T0, T2))
        .containsExactly(bar(T0, "1"), bar(T1, "2"), bar(T2, "3"));
  }

  @Test
  void savingNoBarsClearsTheRange() {
    repository.save(SYMBOL, List.of(bar(T0, "1")), T0, T0);

    repository.save(SYMBOL, List.of(), T0, T0);

    assertThat(repository.read(SYMBOL, T0, T0)).isEmpty();
  }

  @Test
  void readsNothingForASymbolThatWasNeverSaved() {
    assertThat(repository.read(SYMBOL, T0, T2)).isEmpty();
  }

  @Test
  void rejectsABarOutsideTheSavedRange() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> repository.save(SYMBOL, List.of(bar(T2, "3")), T0, T1));
    assertThat(redis.hasKey(BarRedisRepository.keyOf(SYMBOL))).isFalse();
    assertThat(redis.hasKey(BarRedisRepository.spanKeyOf(SYMBOL))).isFalse();
  }

  @Test
  void rejectsARangeThatEndsBeforeItStarts() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> repository.save(SYMBOL, List.of(), T1, T0));
  }

  @Test
  void coversNothingForASymbolThatWasNeverSaved() {
    assertThat(repository.covers(SYMBOL, T0, T0)).isFalse();
  }

  @Test
  void coversTheSavedRangeAndAnyPartOfIt() {
    repository.save(SYMBOL, List.of(bar(T1, "2")), T0, T2);

    assertThat(repository.covers(SYMBOL, T0, T2)).isTrue();
    assertThat(repository.covers(SYMBOL, T1, T1)).isTrue();
  }

  @Test
  void doesNotCoverARangeThatGoesPastTheSavedOne() {
    repository.save(SYMBOL, List.of(bar(T1, "2")), T1, T2);

    assertThat(repository.covers(SYMBOL, T0, T1)).isFalse();
    assertThat(repository.covers(SYMBOL, T2, T3)).isFalse();
  }

  @Test
  void coversARangeSavedWithNoBars() {
    repository.save(SYMBOL, List.of(), T0, T1);

    assertThat(repository.covers(SYMBOL, T0, T1)).isTrue();
  }

  @Test
  void growsTheCoveredRangeWhenANewRangeOverlapsIt() {
    repository.save(SYMBOL, List.of(), T0, T2);

    repository.save(SYMBOL, List.of(), T1, T3);

    assertThat(repository.covers(SYMBOL, T0, T3)).isTrue();
  }

  @Test
  void growsTheCoveredRangeWhenANewRangeTouchesIt() {
    repository.save(SYMBOL, List.of(), T0, T1);

    repository.save(SYMBOL, List.of(), T1, T2);

    assertThat(repository.covers(SYMBOL, T0, T2)).isTrue();
  }

  @Test
  void keepsTheCoveredRangeWhenANewRangeIsInsideIt() {
    repository.save(SYMBOL, List.of(), T0, T3);

    repository.save(SYMBOL, List.of(), T1, T2);

    assertThat(repository.covers(SYMBOL, T0, T3)).isTrue();
  }

  @Test
  void replacesTheCoveredRangeWhenANewRangeIsSeparateFromIt() {
    repository.save(SYMBOL, List.of(), T0, T0);

    repository.save(SYMBOL, List.of(), T2, T3);

    // T1 was never fetched, so the old range cannot be joined to the new one.
    assertThat(repository.covers(SYMBOL, T2, T3)).isTrue();
    assertThat(repository.covers(SYMBOL, T0, T0)).isFalse();
  }

  private static Bar bar(Instant t, String close) {
    BigDecimal price = new BigDecimal(close);
    return new Bar(SYMBOL, t, price, price, price, price, 100, 1, price);
  }
}
