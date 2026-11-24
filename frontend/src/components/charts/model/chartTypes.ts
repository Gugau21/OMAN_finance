export type ChartPricePoint = {
  timestamp: Date;
  price: number;
  volume?: number;
};

export type ChartCandle = {
  timestamp: Date;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};
