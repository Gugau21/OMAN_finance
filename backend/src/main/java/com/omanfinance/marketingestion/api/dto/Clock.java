package com.omanfinance.marketingestion.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Market status exactly as Alpaca {@code GET /v2/clock} returns it. Alpaca sends the times with a
 * New York offset; they are kept as {@link Instant}s.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Clock(
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("is_open") boolean isOpen,
    @JsonProperty("next_open") Instant nextOpen,
    @JsonProperty("next_close") Instant nextClose) {}
