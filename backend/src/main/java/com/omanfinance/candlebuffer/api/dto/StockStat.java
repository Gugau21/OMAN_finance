package com.omanfinance.candlebuffer.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockStat(
    String symbol,
    Instant timestamp,
    BigDecimal price,
    BigDecimal change,
    BigDecimal changePercent,
    long volume) {}
