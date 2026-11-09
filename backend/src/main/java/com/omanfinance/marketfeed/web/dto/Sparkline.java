package com.omanfinance.marketfeed.web.dto;

import com.omanfinance.candlebuffer.api.dto.PricePoint;
import java.util.List;

/** {@code Sparkline} in {@code api_contract.yaml}. */
public record Sparkline(String symbol, List<PricePoint> points) {}
