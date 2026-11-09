package com.omanfinance;

import com.omanfinance.marketingestion.internal.StubAlpacaClock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("fake")
@Import(StubAlpacaClock.Config.class)
class OmanFinanceApplicationTests {

  @Test
  void contextLoads() {}
}
