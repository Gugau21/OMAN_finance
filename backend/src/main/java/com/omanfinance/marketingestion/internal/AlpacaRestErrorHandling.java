package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.AlpacaConfigException;
import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Turns Alpaca's error answers into the exceptions that {@code MarketIngestion} documents, so its
 * callers never see Spring's HTTP client exceptions:
 *
 * <ul>
 *   <li>401 or 403: {@link AlpacaConfigException}, whose message names the environment variables
 *       that hold the API keys.
 *   <li>429: waits as long as {@code Retry-After} says, at most {@code maxRetryWait}, and sends the
 *       request once more. A second 429 is an {@link AlpacaUnavailableException}.
 *   <li>5xx, a timeout or a broken connection: {@link AlpacaUnavailableException}.
 * </ul>
 */
final class AlpacaRestErrorHandling {

  /**
   * How long to wait after a 429 whose {@code Retry-After} is missing or not a number of seconds.
   */
  static final Duration DEFAULT_RETRY_WAIT = Duration.ofSeconds(1);

  /** Waits before the retry after a 429. Tests replace it so they do not really wait. */
  @FunctionalInterface
  interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }

  private AlpacaRestErrorHandling() {}

  /**
   * Adds the error mapping to {@code builder}, so every client it builds has it.
   *
   * @param maxRetryWait the longest wait before the retry after a 429, whatever {@code Retry-After}
   *     says
   * @param sleeper waits before that retry; {@code Thread::sleep} outside tests
   */
  static RestClient.Builder applyTo(
      RestClient.Builder builder, Duration maxRetryWait, Sleeper sleeper) {
    return builder
        .requestInterceptor(
            (request, body, execution) ->
                retryOnceWhenRateLimited(request, body, execution, maxRetryWait, sleeper))
        .defaultStatusHandler(
            AlpacaRestErrorHandling::isRejectedKeys, AlpacaRestErrorHandling::throwRejectedKeys)
        .defaultStatusHandler(
            AlpacaRestErrorHandling::isRateLimited, AlpacaRestErrorHandling::throwStillRateLimited)
        .defaultStatusHandler(
            HttpStatusCode::is5xxServerError, AlpacaRestErrorHandling::throwServerError);
  }

  /**
   * Sends the request and, when Alpaca answers 429, waits for {@link #retryWait} and sends it once
   * more. The second answer is returned whatever it is; a second 429 reaches {@link
   * #throwStillRateLimited}.
   */
  private static ClientHttpResponse retryOnceWhenRateLimited(
      HttpRequest request,
      byte[] body,
      ClientHttpRequestExecution execution,
      Duration maxRetryWait,
      Sleeper sleeper) {
    BufferedResponse response = send(request, body, execution);
    if (!isRateLimited(response.getStatusCode())) {
      return response;
    }
    Duration wait = retryWait(response.getHeaders(), maxRetryWait);
    try {
      sleeper.sleep(wait);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AlpacaUnavailableException(
          "Interrupted while waiting %s to retry %s %s after 429"
              .formatted(wait, request.getMethod(), request.getURI().getPath()),
          e);
    }
    return send(request, body, execution);
  }

  /**
   * Sends the request and reads the whole response body, turning any I/O failure into {@link
   * AlpacaUnavailableException}. The body is read here, not later by the {@code RestClient},
   * because a timeout while reading it there would come out as a plain {@code RestClientException}.
   */
  private static BufferedResponse send(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
    try (ClientHttpResponse response = execution.execute(request, body)) {
      return new BufferedResponse(response, response.getBody().readAllBytes());
    } catch (IOException e) {
      throw new AlpacaUnavailableException(
          "Alpaca did not answer %s %s: %s"
              .formatted(request.getMethod(), request.getURI().getPath(), e),
          e);
    }
  }

  /**
   * How long to wait before retrying a 429: {@code Retry-After} in seconds, or {@link
   * #DEFAULT_RETRY_WAIT} when it is missing, negative or not a number (such as an HTTP date), and
   * never longer than {@code maxRetryWait}.
   */
  static Duration retryWait(HttpHeaders headers, Duration maxRetryWait) {
    Duration wait =
        retryAfterSeconds(headers.getFirst(HttpHeaders.RETRY_AFTER)).orElse(DEFAULT_RETRY_WAIT);
    return wait.compareTo(maxRetryWait) > 0 ? maxRetryWait : wait;
  }

  private static Optional<Duration> retryAfterSeconds(String retryAfter) {
    if (retryAfter == null) {
      return Optional.empty();
    }
    try {
      long seconds = Long.parseLong(retryAfter.trim());
      return seconds < 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(seconds));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static boolean isRejectedKeys(HttpStatusCode status) {
    return status.isSameCodeAs(HttpStatus.UNAUTHORIZED)
        || status.isSameCodeAs(HttpStatus.FORBIDDEN);
  }

  private static boolean isRateLimited(HttpStatusCode status) {
    return status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
  }

  private static void throwRejectedKeys(HttpRequest request, ClientHttpResponse response)
      throws IOException {
    throw new AlpacaConfigException(
        ("Alpaca rejected the API keys with %d on %s %s:"
                + " check ALPACA_API_KEY_ID and ALPACA_API_SECRET_KEY")
            .formatted(
                response.getStatusCode().value(), request.getMethod(), request.getURI().getPath()));
  }

  private static void throwStillRateLimited(HttpRequest request, ClientHttpResponse response) {
    throw new AlpacaUnavailableException(
        "Alpaca answered 429 on %s %s again after one retry"
            .formatted(request.getMethod(), request.getURI().getPath()));
  }

  private static void throwServerError(HttpRequest request, ClientHttpResponse response)
      throws IOException {
    throw new AlpacaUnavailableException(
        "Alpaca answered %d on %s %s"
            .formatted(
                response.getStatusCode().value(), request.getMethod(), request.getURI().getPath()));
  }

  /** A response whose body has already been read, so reading it again cannot fail. */
  private static final class BufferedResponse implements ClientHttpResponse {

    private final HttpStatusCode statusCode;
    private final String statusText;
    private final HttpHeaders headers;
    private final byte[] body;

    BufferedResponse(ClientHttpResponse response, byte[] body) throws IOException {
      this.statusCode = response.getStatusCode();
      this.statusText = response.getStatusText();
      this.headers = HttpHeaders.readOnlyHttpHeaders(response.getHeaders());
      this.body = body;
    }

    @Override
    public HttpStatusCode getStatusCode() {
      return statusCode;
    }

    @Override
    public String getStatusText() {
      return statusText;
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(body);
    }

    @Override
    public void close() {}
  }
}
