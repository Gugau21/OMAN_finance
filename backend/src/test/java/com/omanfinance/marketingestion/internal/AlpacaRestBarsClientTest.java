package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.api.dto.BarsRequest;
import com.omanfinance.marketingestion.api.dto.BarsRequest.Adjustment;
import com.omanfinance.marketingestion.api.dto.BarsRequest.Sort;
import com.omanfinance.marketingestion.api.dto.BarsResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

class AlpacaRestBarsClientTest {

  private static final String BASE_URL = "https://data.alpaca.markets";
  private static final Instant START = Instant.parse("2026-11-05T14:30:00Z");
  private static final Instant END = Instant.parse("2026-11-05T21:00:00Z");
  private static final String EMPTY_BODY = "{\"bars\":{},\"next_page_token\":null}";

  private MockRestServiceServer server;
  private AlpacaRestBarsClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new AlpacaRestBarsClient(builder.build());
  }

  @Test
  void sendsEveryFieldOfTheRequestAndTheIexFeed() {
    server
        .expect(requestTo(startsWith(BASE_URL + AlpacaRestBarsClient.BARS_PATH + "?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(
            request ->
                assertThat(decodedQuery(request))
                    .containsExactlyInAnyOrderEntriesOf(
                        Map.ofEntries(
                            Map.entry("symbols", "AAPL,MSFT"),
                            Map.entry("timeframe", "1Min"),
                            Map.entry("start", "2026-11-05T14:30:00Z"),
                            Map.entry("end", "2026-11-05T21:00:00Z"),
                            Map.entry("limit", "500"),
                            Map.entry("sort", "desc"),
                            Map.entry("adjustment", "split"),
                            Map.entry("asof", "2026-11-05"),
                            Map.entry("currency", "USD"),
                            Map.entry("page_token", "abc"),
                            Map.entry("feed", "iex"))))
        .andRespond(withSuccess(EMPTY_BODY, MediaType.APPLICATION_JSON));

    client.fetchPage(
        new BarsRequest(
            List.of("AAPL", "MSFT"),
            BarsRequest.ONE_MINUTE,
            START,
            END,
            500,
            Sort.DESC,
            Adjustment.SPLIT,
            LocalDate.parse("2026-11-05"),
            "USD",
            "abc"));

    server.verify();
  }

  @Test
  void leavesOutTheOptionalFieldsThatAreNull() {
    server
        .expect(
            request ->
                assertThat(decodedQuery(request))
                    .containsKeys("symbols", "timeframe", "limit", "sort", "adjustment", "feed")
                    .doesNotContainKeys("start", "end", "asof", "page_token"))
        .andRespond(withSuccess(EMPTY_BODY, MediaType.APPLICATION_JSON));

    client.fetchPage(BarsRequest.of(List.of("AAPL"), BarsRequest.ONE_MINUTE, null, null));

    server.verify();
  }

  @Test
  void encodesThePageTokenSoAlpacaGetsItBack() {
    server
        .expect(
            request ->
                assertThat(request.getURI().getRawQuery())
                    .contains("page_token=QUFQTHw%2B%2Fw%3D%3D"))
        .andRespond(withSuccess(EMPTY_BODY, MediaType.APPLICATION_JSON));

    client.fetchPage(
        BarsRequest.of(List.of("AAPL"), BarsRequest.ONE_MINUTE, START, END)
            .withPageToken("QUFQTHw+/w=="));

    server.verify();
  }

  @Test
  void returnsTheBarsExactlyAsAlpacaSendsThem() {
    server
        .expect(requestTo(startsWith(BASE_URL + AlpacaRestBarsClient.BARS_PATH)))
        .andRespond(
            withSuccess(
                new ClassPathResource("alpaca/rest-bars-page-1.json"), MediaType.APPLICATION_JSON));

    BarsResponse response =
        client.fetchPage(
            BarsRequest.of(List.of("AAPL", "MSFT"), BarsRequest.ONE_MINUTE, START, END));

    assertThat(response.bars()).containsOnlyKeys("AAPL", "MSFT");
    assertThat(response.barsFor("AAPL"))
        .containsExactly(
            bar(
                "AAPL",
                "2026-11-05T14:30:00Z",
                "270.14",
                "270.5",
                "269.88",
                "270.31",
                48211,
                612,
                "270.212345"),
            bar(
                "AAPL",
                "2026-11-05T14:31:00Z",
                "270.3",
                "270.42",
                "270.01",
                "270.05",
                21907,
                341,
                "270.198804"));
    assertThat(response.barsFor("MSFT"))
        .containsExactly(
            bar(
                "MSFT",
                "2026-11-05T14:30:00Z",
                "512.07",
                "512.9",
                "511.6",
                "512.66",
                15320,
                287,
                "512.301127"));
    assertThat(response.nextPageToken())
        .isEqualTo("TVNGVHxNfDIwMjYtMTEtMDVUMTQ6MzE6MDAuMDAwMDAwMDAwWg==");
    server.verify();
  }

  @Test
  void stopsAfterThePageThatHoldsLimitBarsEvenWithANextPageToken() {
    // Only one request is expected: a second one would fail the test.
    server
        .expect(request -> assertThat(decodedQuery(request)).containsEntry("limit", "3"))
        .andRespond(
            withSuccess(
                """
                {"bars": {"AAPL": [{"t": "2026-11-05T14:30:00Z"},
                                   {"t": "2026-11-05T14:31:00Z"},
                                   {"t": "2026-11-05T14:32:00Z"}]},
                 "next_page_token": "page-2"}
                """,
                MediaType.APPLICATION_JSON));

    BarsResponse response = client.fetchUpToLimit(request(List.of("AAPL", "MSFT"), 3));

    server.verify();
    assertThat(response.barsFor("AAPL")).hasSize(3);
    assertThat(response.nextPageToken()).isNull();
  }

  @Test
  void followsNextPageTokenAfterAShortPageAndDropsBarsPastLimit() {
    // limit=4 counts bars across both symbols. Page 1 is short, page 2 brings the total to 5:
    // AAPL's bars span both pages, MSFT's second bar is past the limit and page 3 is not fetched.
    server
        .expect(request -> assertThat(decodedQuery(request)).doesNotContainKey("page_token"))
        .andRespond(
            withSuccess(
                """
                {"bars": {"AAPL": [{"t": "2026-11-05T14:30:00Z"}, {"t": "2026-11-05T14:31:00Z"}]},
                 "next_page_token": "page-2"}
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(
            request ->
                assertThat(decodedQuery(request))
                    .containsEntry("page_token", "page-2")
                    .containsEntry("symbols", "AAPL,MSFT")
                    .containsEntry("start", "2026-11-05T14:30:00Z")
                    .containsEntry("limit", "4"))
        .andRespond(
            withSuccess(
                """
                {"bars": {"AAPL": [{"t": "2026-11-05T14:32:00Z"}],
                          "MSFT": [{"t": "2026-11-05T14:30:00Z"}, {"t": "2026-11-05T14:31:00Z"}]},
                 "next_page_token": "page-3"}
                """,
                MediaType.APPLICATION_JSON));

    BarsResponse response = client.fetchUpToLimit(request(List.of("AAPL", "MSFT"), 4));

    server.verify();
    assertThat(response.bars()).containsOnlyKeys("AAPL", "MSFT");
    assertThat(response.barsFor("AAPL"))
        .extracting(Bar::symbol, Bar::timestamp)
        .containsExactly(
            tuple("AAPL", Instant.parse("2026-11-05T14:30:00Z")),
            tuple("AAPL", Instant.parse("2026-11-05T14:31:00Z")),
            tuple("AAPL", Instant.parse("2026-11-05T14:32:00Z")));
    assertThat(response.barsFor("MSFT"))
        .extracting(Bar::symbol, Bar::timestamp)
        .containsExactly(tuple("MSFT", Instant.parse("2026-11-05T14:30:00Z")));
    assertThat(response.nextPageToken()).isNull();
  }

  @Test
  void makesOneRequestWhenAlpacaReturnsNoNextPageToken() {
    server
        .expect(requestTo(startsWith(BASE_URL + AlpacaRestBarsClient.BARS_PATH)))
        .andRespond(
            withSuccess(
                """
                {"bars": {"AAPL": [{"t": "2026-11-05T14:30:00Z"}]}, "next_page_token": null}
                """,
                MediaType.APPLICATION_JSON));

    BarsResponse response =
        client.fetchUpToLimit(BarsRequest.of(List.of("AAPL"), BarsRequest.ONE_MINUTE, START, END));

    server.verify();
    assertThat(response.barsFor("AAPL")).hasSize(1);
    assertThat(response.nextPageToken()).isNull();
  }

  private static BarsRequest request(List<String> symbols, int limit) {
    return new BarsRequest(
        symbols, BarsRequest.ONE_MINUTE, START, END, limit, null, null, null, null, null);
  }

  /** The query of {@code request} with every value decoded, as Alpaca reads it. */
  private static Map<String, String> decodedQuery(ClientHttpRequest request) {
    Map<String, String> query = new LinkedHashMap<>();
    UriComponentsBuilder.fromUri(request.getURI())
        .build()
        .getQueryParams()
        .forEach(
            (name, values) ->
                query.put(name, UriUtils.decode(values.getFirst(), StandardCharsets.UTF_8)));
    return query;
  }

  private static Bar bar(
      String symbol,
      String timestamp,
      String open,
      String high,
      String low,
      String close,
      long volume,
      long tradeCount,
      String vwap) {
    return new Bar(
        symbol,
        Instant.parse(timestamp),
        new BigDecimal(open),
        new BigDecimal(high),
        new BigDecimal(low),
        new BigDecimal(close),
        volume,
        tradeCount,
        new BigDecimal(vwap));
  }
}
