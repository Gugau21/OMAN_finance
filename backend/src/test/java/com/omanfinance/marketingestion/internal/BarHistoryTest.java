package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsRequest.Adjustment;
import com.omanfinance.marketingestion.api.dto.BarsRequest.Sort;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Real Redis, as in {@link BarRedisRepositoryTest}, and Alpaca faked with {@link
 * MockRestServiceServer}: a request the test did not expect fails it, so "no HTTP call" is checked
 * by expecting fewer calls than {@code getBars} is called.
 */
@DataRedisTest
@Import({BarRedisRepository.class, TestClock.Config.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@EnableConfigurationProperties(AlpacaProperties.class)
class BarHistoryTest {

  private static final String BASE_URL = "https://data.alpaca.markets";
  private static final String A = "TEST_CACHE_A";
  private static final String B = "TEST_CACHE_B";
  private static final Instant T0 = Instant.parse("2026-11-05T14:30:00Z");
  private static final Instant T1 = T0.plusSeconds(60);
  private static final Instant T2 = T0.plusSeconds(120);
  private static final Instant T5 = T0.plusSeconds(300);
  private static final Instant T10 = T0.plusSeconds(600);

  /** Long after T10, so every minute in these tests has settled. */
  private static final Instant NOW = T0.plusSeconds(3600);

  @Autowired private BarRedisRepository cache;
  @Autowired private StringRedisTemplate redis;
  @Autowired private ObjectMapper json;
  @Autowired private TestClock clock;

  private MockRestServiceServer alpaca;
  private BarHistory client;

  @BeforeEach
  void setUp() {
    deleteTestKeys();
    clock.set(NOW);
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    alpaca = MockRestServiceServer.bindTo(builder).build();
    client = new BarHistory(new AlpacaBarsClient(builder.build()), cache, clock);
  }

  @AfterEach
  void deleteTestKeys() {
    redis.delete(List.of(BarRedisRepository.keyOf(A), BarRedisRepository.keyOf(B)));
  }

  @Test
  void answersASecondIdenticalRequestFromTheCacheWithTheSameBars() throws Exception {
    BarsResponse fromAlpaca =
        new BarsResponse(
            Map.of(A, List.of(bar(A, T0, "1"), bar(A, T2, "3")), B, List.of(bar(B, T1, "2"))),
            null);
    alpacaAnswers(once(), fromAlpaca);
    BarsRequest request = request(List.of(A, B), T0, T5);

    BarsResponse first = client.getBars(request);
    BarsResponse second = client.getBars(request);

    alpaca.verify();
    assertThat(first).isEqualTo(fromAlpaca);
    assertThat(second).isEqualTo(fromAlpaca);
  }

  @Test
  void leavesOutASymbolWithNoBarsLikeAlpaca() throws Exception {
    BarsResponse fromAlpaca = new BarsResponse(Map.of(A, List.of(bar(A, T0, "1"))), null);
    alpacaAnswers(once(), fromAlpaca);
    BarsRequest request = request(List.of(A, B), T0, T5);

    client.getBars(request);
    BarsResponse fromCache = client.getBars(request);

    alpaca.verify();
    assertThat(fromCache.bars()).containsOnlyKeys(A);
  }

  @Test
  void answersADescendingRequestFromTheCacheNewestFirst() throws Exception {
    alpacaAnswers(
        once(), new BarsResponse(Map.of(A, List.of(bar(A, T0, "1"), bar(A, T2, "3"))), null));
    client.getBars(request(List.of(A), T0, T5));

    BarsResponse fromCache = client.getBars(request(List.of(A), T0, T5, Sort.DESC, Adjustment.RAW));

    alpaca.verify();
    assertThat(fromCache.barsFor(A)).containsExactly(bar(A, T2, "3"), bar(A, T0, "1"));
  }

  @Test
  void answersAPartOfACachedRangeFromTheCache() throws Exception {
    alpacaAnswers(
        once(), new BarsResponse(Map.of(A, List.of(bar(A, T0, "1"), bar(A, T2, "3"))), null));
    client.getBars(request(List.of(A), T0, T5));

    BarsResponse fromCache = client.getBars(request(List.of(A), T1, T2));

    alpaca.verify();
    assertThat(fromCache.barsFor(A)).containsExactly(bar(A, T2, "3"));
  }

  @Test
  void asksAlpacaWhenPartOfTheRangeIsNotCached() throws Exception {
    alpacaAnswers(times(2), new BarsResponse(Map.of(A, List.of(bar(A, T0, "1"))), null));

    client.getBars(request(List.of(A), T0, T5));
    client.getBars(request(List.of(A), T0, T10));

    alpaca.verify();
  }

  @Test
  void asksAlpacaForEverySymbolWhenOneIsNotCached() throws Exception {
    alpacaAnswers(times(2), new BarsResponse(Map.of(A, List.of(bar(A, T0, "1"))), null));

    client.getBars(request(List.of(A), T0, T5));
    client.getBars(request(List.of(A, B), T0, T5));

    alpaca.verify();
  }

  @Test
  void cachesOnlyMinutesThatHaveSettled() throws Exception {
    // At T5 + 30 s, T5 is still open and T4 ended only 30 s ago, so T3 is the last settled minute.
    clock.set(T5.plusSeconds(30));
    alpacaAnswers(times(2), new BarsResponse(Map.of(A, List.of(bar(A, T0, "1"))), null));

    client.getBars(request(List.of(A), T0, T5));
    BarsResponse settledPart = client.getBars(request(List.of(A), T0, T0.plusSeconds(180)));
    client.getBars(request(List.of(A), T0, T5));

    alpaca.verify();
    assertThat(settledPart.barsFor(A)).containsExactly(bar(A, T0, "1"));
  }

  @Test
  void cachesNothingWhenNoRequestedMinuteHasSettled() throws Exception {
    clock.set(T0.plusSeconds(30));
    alpacaAnswers(once(), new BarsResponse(Map.of(), null));

    client.getBars(request(List.of(A), T0, T0));

    alpaca.verify();
    assertThat(redis.hasKey(BarRedisRepository.keyOf(A))).isFalse();
  }

  @Test
  void passesARequestTheCacheCannotAnswerStraightToAlpaca() throws Exception {
    alpacaAnswers(times(3), new BarsResponse(Map.of(A, List.of(bar(A, T0, "1"))), null));

    BarsRequest split = request(List.of(A), T0, T5, Sort.ASC, Adjustment.SPLIT);
    client.getBars(split);
    client.getBars(split);
    client.getBars(request(List.of(A), T0.plusSeconds(1), T5));

    alpaca.verify();
    assertThat(redis.hasKey(BarRedisRepository.keyOf(A))).isFalse();
  }

  @Test
  void answersOnlyTheFirstLimitBarsInSortOrderFromTheCache() throws Exception {
    alpacaAnswers(
        once(),
        new BarsResponse(
            Map.of(A, List.of(bar(A, T0, "1"), bar(A, T1, "2"), bar(A, T2, "3"))), null));
    client.getBars(request(List.of(A), T0, T5));

    BarsResponse fromCache = client.getBars(request(List.of(A), T0, T5, Sort.DESC, 2));

    alpaca.verify();
    assertThat(fromCache.barsFor(A)).containsExactly(bar(A, T2, "3"), bar(A, T1, "2"));
  }

  @Test
  void asksAlpacaWhenTheCachedBarsOfSeveralSymbolsAreMoreThanLimit() throws Exception {
    // Which bars Alpaca keeps then depends on how it orders the symbols.
    alpacaAnswers(
        times(2),
        new BarsResponse(Map.of(A, List.of(bar(A, T0, "1")), B, List.of(bar(B, T0, "2"))), null));
    client.getBars(request(List.of(A, B), T0, T5));

    client.getBars(request(List.of(A, B), T0, T5, Sort.ASC, 1));

    alpaca.verify();
  }

  @Test
  void cachesAnAscendingResponseCutByTheLimitOnlyUpToItsLastBar() throws Exception {
    alpacaAnswers(
        times(2), new BarsResponse(Map.of(A, List.of(bar(A, T0, "1"), bar(A, T1, "2"))), null));

    client.getBars(request(List.of(A), T0, T10, Sort.ASC, 2));
    BarsResponse reached = client.getBars(request(List.of(A), T0, T1));
    client.getBars(request(List.of(A), T0, T2));

    alpaca.verify();
    assertThat(reached.barsFor(A)).containsExactly(bar(A, T0, "1"), bar(A, T1, "2"));
  }

  @Test
  void cachesADescendingResponseCutByTheLimitOnlyFromItsLastBar() throws Exception {
    // Newest first: T10, then T5, so T6-T9 had no trades and T0-T4 were not fetched.
    alpacaAnswers(
        times(2), new BarsResponse(Map.of(A, List.of(bar(A, T10, "2"), bar(A, T5, "1"))), null));

    client.getBars(request(List.of(A), T0, T10, Sort.DESC, 2));
    BarsResponse reached = client.getBars(request(List.of(A), T5, T10));
    client.getBars(request(List.of(A), T0, T10));

    alpaca.verify();
    assertThat(reached.barsFor(A)).containsExactly(bar(A, T5, "1"), bar(A, T10, "2"));
  }

  @Test
  void cachesNothingWhenTheLimitCutsAResponseForSeveralSymbols() throws Exception {
    alpacaAnswers(
        once(),
        new BarsResponse(Map.of(A, List.of(bar(A, T0, "1")), B, List.of(bar(B, T0, "2"))), null));

    client.getBars(request(List.of(A, B), T0, T5, Sort.ASC, 2));

    alpaca.verify();
    assertThat(redis.hasKey(BarRedisRepository.keyOf(A))).isFalse();
    assertThat(redis.hasKey(BarRedisRepository.keyOf(B))).isFalse();
  }

  private void alpacaAnswers(ExpectedCount count, BarsResponse response)
      throws JsonProcessingException {
    alpaca
        .expect(count, requestTo(startsWith(BASE_URL + AlpacaBarsClient.BARS_PATH)))
        .andRespond(withSuccess(json.writeValueAsString(response), MediaType.APPLICATION_JSON));
  }

  private static BarsRequest request(List<String> symbols, Instant start, Instant end) {
    return BarsRequest.of(symbols, BarsRequest.ONE_MINUTE, start, end);
  }

  private static BarsRequest request(
      List<String> symbols, Instant start, Instant end, Sort sort, Adjustment adjustment) {
    return new BarsRequest(
        symbols, BarsRequest.ONE_MINUTE, start, end, null, sort, adjustment, null, null, null);
  }

  private static BarsRequest request(
      List<String> symbols, Instant start, Instant end, Sort sort, int limit) {
    return new BarsRequest(
        symbols, BarsRequest.ONE_MINUTE, start, end, limit, sort, null, null, null, null);
  }

  private static Bar bar(String symbol, Instant t, String close) {
    BigDecimal price = new BigDecimal(close);
    return new Bar(symbol, t, price, price, price, price, 100, 1, price);
  }
}
