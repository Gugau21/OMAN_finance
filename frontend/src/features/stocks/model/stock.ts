import type { ChartCandle, ChartPricePoint } from '../../../components/charts/model/chartTypes';

export type StockListItem = {
  symbol: string;
  name: string;
  timestamp: Date;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  pricePoints: ChartPricePoint[];
};

export type Stock = {
  symbol: string;
  name: string;
};

export type Sparkline = {
  symbol: string;
  points: ChartPricePoint[];
};

export type StockStat = {
  symbol: string;
  timestamp: Date;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
};

export type StockCandles = {
  symbol: string;
  candles: ChartCandle[];
};

export type StockPageView = {
  candles: StockCandles;
  stat: StockStat;
  sparkline: ChartPricePoint[];
};
