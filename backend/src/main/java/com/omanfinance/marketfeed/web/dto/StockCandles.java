package com.omanfinance.marketfeed.web.dto;

import java.util.List;

/** {@code StockCandles} in {@code api_contract.yaml}. */
public record StockCandles(String symbol, List<ContractCandle> candles) {}
