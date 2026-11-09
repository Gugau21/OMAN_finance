package com.omanfinance.marketingestion.internal;

import com.omanfinance.marketingestion.api.AlpacaConfigException;
import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 *   <li>5xx, a timeout or a broken connection: {@link AlpacaUnavailableException}.
 * </ul>
 */
final class AlpacaErrorHandling {

  private AlpacaErrorHandling() {}

  /** Adds the error mapping to {@code builder}, so every client it builds has it. */
  static RestClient.Builder applyTo(RestClient.Builder builder) {
    return builder
        .requestInterceptor(AlpacaErrorHandling::failWhenUnreachable)
        .defaultStatusHandler(
            AlpacaErrorHandling::isRejectedKeys, AlpacaErrorHandling::throwRejectedKeys)
        .defaultStatusHandler(
            HttpStatusCode::is5xxServerError, AlpacaErrorHandling::throwServerError);
  }

  /**
   * Sends the request and reads the whole response body, turning any I/O failure into {@link
   * AlpacaUnavailableException}. The body is read here, not later by the {@code RestClient},
   * because a timeout while reading it there would come out as a plain {@code RestClientException}.
   */
  private static ClientHttpResponse failWhenUnreachable(
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

  private static boolean isRejectedKeys(HttpStatusCode status) {
    return status.isSameCodeAs(HttpStatus.UNAUTHORIZED)
        || status.isSameCodeAs(HttpStatus.FORBIDDEN);
  }

  private static void throwRejectedKeys(HttpRequest request, ClientHttpResponse response)
      throws IOException {
    throw new AlpacaConfigException(
        ("Alpaca rejected the API keys with %d on %s %s:"
                + " check ALPACA_API_KEY_ID and ALPACA_API_SECRET_KEY")
            .formatted(
                response.getStatusCode().value(), request.getMethod(), request.getURI().getPath()));
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
