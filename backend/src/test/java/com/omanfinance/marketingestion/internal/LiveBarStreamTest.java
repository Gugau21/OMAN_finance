package com.omanfinance.marketingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.omanfinance.marketingestion.api.dto.Bar;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class LiveBarStreamTest {

  private static final Instant MINUTE = Instant.parse("2026-11-13T14:30:00Z");

  private final LiveBarStream stream = new LiveBarStream();

  @Test
  void sendsEachBarToEverySubscriber() {
    List<Bar> first = new CopyOnWriteArrayList<>();
    List<Bar> second = new CopyOnWriteArrayList<>();
    stream.flux().subscribe(first::add);
    stream.flux().subscribe(second::add);

    stream.publish(bar("AAPL", 0));
    stream.publish(bar("MSFT", 0));

    assertThat(first).containsExactly(bar("AAPL", 0), bar("MSFT", 0));
    assertThat(second).containsExactly(bar("AAPL", 0), bar("MSFT", 0));
  }

  @Test
  void aSubscriberThatThrowsDoesNotStopTheOthers() {
    List<Bar> received = new CopyOnWriteArrayList<>();
    stream
        .flux()
        .subscribe(
            bar -> {
              throw new IllegalStateException("subscriber bug");
            },
            error -> {});
    stream.flux().subscribe(received::add);

    assertThatCode(
            () -> {
              stream.publish(bar("AAPL", 0));
              stream.publish(bar("AAPL", 1));
            })
        .doesNotThrowAnyException();

    assertThat(received).containsExactly(bar("AAPL", 0), bar("AAPL", 1));
  }

  @Test
  void doesNotReplayBarsPublishedBeforeASubscriberJoined() {
    stream.publish(bar("AAPL", 0));
    List<Bar> late = new CopyOnWriteArrayList<>();
    stream.flux().subscribe(late::add);

    stream.publish(bar("AAPL", 1));

    assertThat(late).containsExactly(bar("AAPL", 1));
  }

  @Test
  void dropsABarQuietlyWhenNobodyListens() {
    assertThatCode(() -> stream.publish(bar("AAPL", 0))).doesNotThrowAnyException();
  }

  @Test
  void staysOpenAfterTheLastSubscriberLeaves() {
    AtomicBoolean ended = new AtomicBoolean();
    Disposable first =
        stream.flux().subscribe(bar -> {}, error -> ended.set(true), () -> ended.set(true));
    first.dispose();
    List<Bar> next = new CopyOnWriteArrayList<>();
    stream.flux().subscribe(next::add, error -> ended.set(true), () -> ended.set(true));

    stream.publish(bar("AAPL", 0));

    assertThat(next).containsExactly(bar("AAPL", 0));
    assertThat(ended).isFalse();
  }

  @Test
  void deliversEveryBarPublishedFromSeveralThreadsAtOnce() throws InterruptedException {
    List<Bar> received = new CopyOnWriteArrayList<>();
    stream.flux().subscribe(received::add);
    ExecutorService threads = Executors.newFixedThreadPool(4);

    IntStream.range(0, 4)
        .forEach(
            thread ->
                threads.submit(
                    () ->
                        IntStream.range(0, 250)
                            .forEach(i -> stream.publish(bar("T" + thread, i)))));
    threads.shutdown();

    assertThat(threads.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(received).hasSize(1000).doesNotHaveDuplicates();
  }

  private static Bar bar(String symbol, int minute) {
    BigDecimal price = BigDecimal.valueOf(100 + minute);
    return new Bar(
        symbol, MINUTE.plusSeconds(60L * minute), price, price, price, price, 10, 1, price);
  }
}
