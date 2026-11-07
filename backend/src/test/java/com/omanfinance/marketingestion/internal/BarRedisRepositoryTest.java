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
 * Runs against the real Redis that {@code spring.data.redis.*} points at. Only touches the key of
 * {@link #SYMBOL}, so it does not disturb bars cached by a running backend.
 */
@DataRedisTest
@Import(BarRedisRepository.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class BarRedisRepositoryTest {

  private static final String SYMBOL = "TEST_BARS";
  private static final String KEY = BarRedisRepository.keyOf(SYMBOL);
  private static final Instant T0 = Instant.parse("2026-11-05T14:30:00Z");
  private static final Instant T1 = T0.plusSeconds(60);
  private static final Instant T2 = T0.plusSeconds(120);
  private static final Instant T3 = T0.plusSeconds(180);

  @Autowired private BarRedisRepository repository;
  @Autowired private StringRedisTemplate redis;
  @Autowired private ObjectMapper json;

  @BeforeEach
  @AfterEach
  void deleteTestKey() {
    redis.delete(KEY);
  }

  @Test
  void returnsTheRecordedAlpacaBarsUnchanged() throws IOException {
    BarsResponse recorded =
        json.readValue(
            new ClassPathResource("alpaca/bars-page-1.json").getInputStream(), BarsResponse.class);
    List<Bar> bars = recorded.barsFor("AAPL").stream().map(bar -> bar.withSymbol(SYMBOL)).toList();

    repository.save(SYMBOL, bars, T0, T1);

    // Record equality compares BigDecimal with equals, so 270.5 must not come back as 270.50.
    assertThat(repository.readIfComplete(SYMBOL, T0, T1)).contains(bars);
  }

  @Test
  void returnsOnlyTheBarsInTheRangeWithBothEndsIncluded() {
    repository.save(SYMBOL, List.of(bar(T0, "1"), bar(T1, "2"), bar(T2, "3")), T0, T2);

    assertThat(repository.readIfComplete(SYMBOL, T1, T2))
        .contains(List.of(bar(T1, "2"), bar(T2, "3")));
    assertThat(repository.readIfComplete(SYMBOL, T1, T1)).contains(List.of(bar(T1, "2")));
  }

  @Test
  void returnsBarsOldestFirstWhateverOrderTheyWereSavedIn() {
    repository.save(SYMBOL, List.of(bar(T2, "3"), bar(T0, "1"), bar(T1, "2")), T0, T2);

    assertThat(repository.readIfComplete(SYMBOL, T0, T2))
        .contains(List.of(bar(T0, "1"), bar(T1, "2"), bar(T2, "3")));
  }

  @Test
  void storesOneEntryPerMinuteIncludingMinutesWithoutABar() {
    repository.save(SYMBOL, List.of(bar(T1, "2")), T0, T2);

    assertThat(redis.opsForZSet().zCard(KEY)).isEqualTo(3);
  }

  @Test
  void treatsAMinuteWithoutABarAsFetched() {
    repository.save(SYMBOL, List.of(bar(T0, "1"), bar(T2, "3")), T0, T2);

    assertThat(repository.readIfComplete(SYMBOL, T0, T2))
        .contains(List.of(bar(T0, "1"), bar(T2, "3")));
  }

  @Test
  void treatsARangeSavedWithNoBarsAsFetched() {
    repository.save(SYMBOL, List.of(), T0, T1);

    assertThat(repository.readIfComplete(SYMBOL, T0, T1)).contains(List.of());
  }

  @Test
  void returnsNothingForARangeThatWasNeverSaved() {
    assertThat(repository.readIfComplete(SYMBOL, T0, T2)).isEmpty();
  }

  @Test
  void returnsNothingWhenPartOfTheRangeWasNotSaved() {
    repository.save(SYMBOL, List.of(bar(T1, "2")), T1, T2);

    assertThat(repository.readIfComplete(SYMBOL, T0, T2)).isEmpty();
    assertThat(repository.readIfComplete(SYMBOL, T1, T3)).isEmpty();
  }

  @Test
  void keepsTwoSeparateRangesWithTheGapBetweenThemMissing() {
    repository.save(SYMBOL, List.of(bar(T0, "1")), T0, T0);
    repository.save(SYMBOL, List.of(bar(T3, "4")), T2, T3);

    assertThat(repository.readIfComplete(SYMBOL, T0, T0)).contains(List.of(bar(T0, "1")));
    assertThat(repository.readIfComplete(SYMBOL, T2, T3)).contains(List.of(bar(T3, "4")));
    assertThat(repository.readIfComplete(SYMBOL, T0, T3)).isEmpty();
  }

  @Test
  void joinsOverlappingRanges() {
    repository.save(SYMBOL, List.of(bar(T0, "1")), T0, T1);
    repository.save(SYMBOL, List.of(bar(T2, "3")), T1, T3);

    assertThat(repository.readIfComplete(SYMBOL, T0, T3))
        .contains(List.of(bar(T0, "1"), bar(T2, "3")));
  }

  @Test
  void savingARangeAgainReplacesWhatWasInIt() {
    repository.save(SYMBOL, List.of(bar(T0, "1"), bar(T1, "1")), T0, T1);

    repository.save(SYMBOL, List.of(bar(T1, "2")), T0, T1);

    // T0 lost its bar, T1 got a new one, and neither minute has two entries.
    assertThat(repository.readIfComplete(SYMBOL, T0, T1)).contains(List.of(bar(T1, "2")));
    assertThat(redis.opsForZSet().zCard(KEY)).isEqualTo(2);
  }

  @Test
  void savingKeepsWhatIsOutsideTheRange() {
    repository.save(SYMBOL, List.of(bar(T0, "1"), bar(T2, "3")), T0, T2);

    repository.save(SYMBOL, List.of(bar(T1, "2")), T1, T1);

    assertThat(repository.readIfComplete(SYMBOL, T0, T2))
        .contains(List.of(bar(T0, "1"), bar(T1, "2"), bar(T2, "3")));
  }

  @Test
  void countsOnlyTheWholeMinutesInARangeThatIsNotOnWholeMinutes() {
    Instant halfPastT0 = T0.plusSeconds(30);

    repository.save(SYMBOL, List.of(bar(T1, "2")), halfPastT0, T2);

    assertThat(repository.readIfComplete(SYMBOL, halfPastT0, T2)).contains(List.of(bar(T1, "2")));
    assertThat(repository.readIfComplete(SYMBOL, T0, T2)).isEmpty();
  }

  @Test
  void rejectsABarOutsideTheSavedRange() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> repository.save(SYMBOL, List.of(bar(T2, "3")), T0, T1));
    assertThat(redis.hasKey(KEY)).isFalse();
  }

  @Test
  void rejectsABarThatIsNotOnAWholeMinute() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> repository.save(SYMBOL, List.of(bar(T0.plusSeconds(1), "1")), T0, T1));
    assertThat(redis.hasKey(KEY)).isFalse();
  }

  @Test
  void rejectsARangeThatEndsBeforeItStarts() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> repository.save(SYMBOL, List.of(), T1, T0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> repository.readIfComplete(SYMBOL, T1, T0));
  }

  private static Bar bar(Instant t, String close) {
    BigDecimal price = new BigDecimal(close);
    return new Bar(SYMBOL, t, price, price, price, price, 100, 1, price);
  }
}
