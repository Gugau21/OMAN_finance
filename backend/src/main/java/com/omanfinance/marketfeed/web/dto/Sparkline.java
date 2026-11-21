package com.omanfinance.marketfeed.web.dto;

import java.util.List;

/** {@code Sparkline} in {@code api_contract.yaml}. */
public record Sparkline(String symbol, List<ContractPricePoint> points) {}
