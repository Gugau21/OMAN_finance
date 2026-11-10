package com.omanfinance;

import com.omanfinance.marketingestion.internal.StubAlpacaClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = TestRedisDatabase.PROPERTY)
@ActiveProfiles("fake")
@Import(StubAlpacaClock.Config.class)
class OmanFinanceApplicationTests {

  @AfterAll
  static void deleteWhatStartupSaved(@Autowired StringRedisTemplate redis) {
    TestRedisDatabase.flush(redis);
  }

  @Test
  void contextLoads() {}
}
