package com.omanfinance.marketfeed.web;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import java.nio.file.Path;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Checks MockMvc requests and responses against {@code docs/api_contract.yaml}. Gradle runs the
 * tests from {@code backend/}, so the contract is one directory up.
 */
final class ApiContract {

  private static final OpenApiInteractionValidator VALIDATOR =
      OpenApiInteractionValidator.createForSpecificationUrl(
              Path.of("../docs/api_contract.yaml").toAbsolutePath().toUri().toString())
          .build();

  private ApiContract() {}

  /** Fails when the request or response does not match the contract for its path and method. */
  static ResultMatcher matchesContract() {
    return OpenApiValidationMatchers.openApi().isValid(VALIDATOR);
  }
}
