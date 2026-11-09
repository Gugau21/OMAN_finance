package com.omanfinance.clientfeed;

import com.omanfinance.marketingestion.api.AlpacaUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the errors of every REST controller to the status codes in {@code api_contract.yaml}.
 *
 * <p>The handlers only call {@link HttpServletResponse#sendError(int)}, so Spring Boot's {@code
 * BasicErrorController} writes its default error body ({@code timestamp}, {@code status}, {@code
 * error}, {@code path}), the {@code Error} schema of the contract. Any other exception falls
 * through to Spring Boot as a 500 with the same body.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler({MarketDataNotReadyException.class, AlpacaUnavailableException.class})
  void handleServiceUnavailable(RuntimeException e, HttpServletResponse response)
      throws IOException {
    log.warn("Market data unavailable: {}", e.getMessage());
    response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
  }

  @ExceptionHandler(StockNotFoundException.class)
  void handleStockNotFound(HttpServletResponse response) throws IOException {
    response.sendError(HttpStatus.NOT_FOUND.value());
  }
}
