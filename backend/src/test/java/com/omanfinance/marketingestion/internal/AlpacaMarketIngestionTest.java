package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.omanfinance.marketingestion.api.MarketIngestion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AlpacaMarketIngestionTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(AlpacaMarketIngestion.class, FakeMarketIngestion.class);

  @Test
  void isTheOnlyMarketIngestionWithoutTheFakeProfile() {
    runner.run(
        context ->
            assertThat(context)
                .getBean(MarketIngestion.class)
                .isInstanceOf(AlpacaMarketIngestion.class));
    runner
        .withPropertyValues("spring.profiles.active=prod")
        .run(
            context ->
                assertThat(context)
                    .getBean(MarketIngestion.class)
                    .isInstanceOf(AlpacaMarketIngestion.class));
  }

  @Test
  void stepsAsideForTheFakeProfile() {
    runner
        .withPropertyValues("spring.profiles.active=fake")
        .run(
            context ->
                assertThat(context)
                    .getBean(MarketIngestion.class)
                    .isInstanceOf(FakeMarketIngestion.class));
  }
}
