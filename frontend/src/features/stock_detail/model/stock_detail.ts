import type { ChartPricePoint, ChartCandle } from '../../../components/charts/model/chartTypes';

export type StockDetail = {
  symbol: string;
  stat: StockStat;
  sparkline: Sparkline;
  candles: Candles;
};

export type StockStat = {
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  timestamp: Date;
};

export type Sparkline = {
  points: ChartPricePoint[];
};

export type Candles = {
  candles: ChartCandle[];
};
