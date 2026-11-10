package com.omanfinance;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * The Redis database that tests starting the whole application use. At startup the application
 * backfills {@code candles:*} for every tracked stock, so in database 0 those tests would overwrite
 * the candles of a backend running against the same Redis with fake ones.
 *
 * <p>Put {@link #PROPERTY} in {@code @SpringBootTest(properties = ...)} and call {@link #flush}
 * from an {@code @AfterAll} method, so the test leaves nothing behind.
 */
public final class TestRedisDatabase {

  public static final int INDEX = 15;

  public static final String PROPERTY = "spring.data.redis.database=" + INDEX;

  private TestRedisDatabase() {}

  /**
   * Deletes every key of database {@link #INDEX}.
   *
   * @throws IllegalStateException when {@code redis} is connected to another database, so a test
   *     without {@link #PROPERTY} cannot wipe database 0
   */
  public static void flush(StringRedisTemplate redis) {
    if (!(redis.getConnectionFactory() instanceof LettuceConnectionFactory factory)
        || factory.getDatabase() != INDEX) {
      throw new IllegalStateException(
          "Refusing to flush: the test is not on Redis database " + INDEX);
    }
    redis.execute(
        (RedisCallback<Void>)
            (RedisConnection connection) -> {
              connection.serverCommands().flushDb();
              return null;
            });
  }
}
