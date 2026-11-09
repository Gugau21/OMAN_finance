package com.omanfinance;

import com.omanfinance.marketingestion.internal.StubAlpacaClock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(StubAlpacaClock.Config.class)
class OmanFinanceApplicationTests {

  @Test
  void contextLoads() {}
}
