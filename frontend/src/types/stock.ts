export type Stock = {
  symbol: string;
  name: string;
};

export type PricePoint = {
  timestamp: string;
  price: number;
};

export type Sparkline = {
  symbol: string;
  points: PricePoint[];
};

export type StockStat = {
  symbol: string;
  timestamp: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
};

export type MainPageStock = {
  symbol: string;
  name: string;
  timestamp: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  pricePoints: PricePoint[];
};
