import type { ChartPricePoint } from '../../../components/charts/model/chartTypes';

export type StockListItem = {
  symbol: string;
  name: string;
  stat: StockStat;
  sparkline: Sparkline;
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
