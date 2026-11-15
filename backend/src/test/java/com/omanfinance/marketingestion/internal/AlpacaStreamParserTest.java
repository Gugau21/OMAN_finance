package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omanfinance.marketingestion.api.dto.Bar;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Authenticated;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.BarMessage;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Connected;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.ErrorMessage;
import com.omanfinance.marketingestion.internal.AlpacaStreamMessage.Subscribed;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AlpacaStreamParserTest {

  private final AlpacaStreamParser parser =
      new AlpacaStreamParser(new ObjectMapper().findAndRegisterModules());

  @Test
  void parsesConnected() throws IOException {
    assertThat(parser.parse(frame("stream-connected.json"))).containsExactly(new Connected());
  }

  @Test
  void parsesAuthenticated() throws IOException {
    assertThat(parser.parse(frame("stream-authenticated.json")))
        .containsExactly(new Authenticated());
  }

  @Test
  void parsesTheSubscribedBarSymbols() throws IOException {
    assertThat(parser.parse(frame("stream-subscription.json")))
        .containsExactly(new Subscribed(List.of("AAPL", "MSFT")));
  }

  @Test
  void parsesEveryBarOfAFrameInOrderWithValuesUnchanged() throws IOException {
    List<AlpacaStreamMessage> messages = parser.parse(frame("stream-bars.json"));

    assertThat(messages).hasSize(2).allMatch(BarMessage.class::isInstance);
    Bar aapl = ((BarMessage) messages.get(0)).bar();
    assertThat(aapl.symbol()).isEqualTo("AAPL");
    assertThat(aapl.timestamp()).isEqualTo(Instant.parse("2026-11-13T14:30:00Z"));
    assertThat(aapl.open()).isEqualTo(new BigDecimal("270.14"));
    assertThat(aapl.high()).isEqualTo(new BigDecimal("270.5"));
    assertThat(aapl.low()).isEqualTo(new BigDecimal("269.88"));
    assertThat(aapl.close()).isEqualTo(new BigDecimal("270.31"));
    assertThat(aapl.volume()).isEqualTo(48211);
    assertThat(aapl.tradeCount()).isEqualTo(612);
    assertThat(aapl.vwap()).isEqualTo(new BigDecimal("270.212345"));
    assertThat(((BarMessage) messages.get(1)).bar().symbol()).isEqualTo("MSFT");
  }

  @Test
  void keepsPricesExactlyAsSent() {
    String frame =
        "[{\"T\":\"b\",\"S\":\"AAPL\",\"o\":270.10,\"h\":270.5,\"l\":269.88,\"c\":270.31,"
            + "\"v\":1,\"t\":\"2026-11-13T14:30:00Z\",\"n\":1,\"vw\":270.212345678901234567}]";

    Bar bar = ((BarMessage) parser.parse(frame).get(0)).bar();

    assertThat(bar.open()).isEqualTo(new BigDecimal("270.10"));
    assertThat(bar.vwap()).isEqualTo(new BigDecimal("270.212345678901234567"));
  }

  @Test
  void skipsMessageTypesItDoesNotUseAndKeepsTheOrderOfTheRest() throws IOException {
    List<AlpacaStreamMessage> messages = parser.parse(frame("stream-mixed.json"));

    assertThat(messages).hasSize(2);
    assertThat(messages.get(0)).isEqualTo(new Subscribed(List.of("AAPL")));
    assertThat(((BarMessage) messages.get(1)).bar().timestamp())
        .isEqualTo(Instant.parse("2026-11-13T14:31:00Z"));
  }

  @Test
  void parsesAuthFailed() throws IOException {
    assertThat(parser.parse(frame("stream-error-auth-failed.json")))
        .containsExactly(new ErrorMessage(402, "auth failed"));
  }

  @Test
  void parsesConnectionLimitExceeded() throws IOException {
    assertThat(parser.parse(frame("stream-error-connection-limit.json")))
        .containsExactly(new ErrorMessage(406, "connection limit exceeded"));
  }

  @Test
  void skipsUnknownSuccessMessagesAndMessagesWithoutAType() {
    assertThat(parser.parse("[{\"T\":\"success\",\"msg\":\"something new\"},{\"msg\":\"x\"}]"))
        .isEmpty();
  }

  @Test
  void parsesAnEmptyFrame() {
    assertThat(parser.parse("[]")).isEmpty();
  }

  @Test
  void rejectsAFrameThatIsNotAJsonArray() {
    assertThatThrownBy(() -> parser.parse("{\"T\":\"success\",\"msg\":\"connected\"}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> parser.parse("[{\"T\":")).isInstanceOf(IllegalArgumentException.class);
  }

  private static String frame(String name) throws IOException {
    return new ClassPathResource("alpaca/" + name).getContentAsString(StandardCharsets.UTF_8);
  }
}
