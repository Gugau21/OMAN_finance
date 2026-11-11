import type { MainPageStock, Sparkline, Stock, StockStat } from '../types/stock';

export function createMainPageStocks(
  stocks: Stock[],
  stats: StockStat[],
  sparklines: Sparkline[],
): MainPageStock[] {
  const statsBySymbol = new Map(stats.map((stat) => [stat.symbol, stat]));
  const sparklinesBySymbol = new Map(sparklines.map((sparkline) => [sparkline.symbol, sparkline]));

  return stocks.flatMap((stock) => {
    const stat = statsBySymbol.get(stock.symbol);
    const sparkline = sparklinesBySymbol.get(stock.symbol);

    if (!stat || !sparkline) {
      return [];
    }

    return [
      {
        symbol: stock.symbol,
        name: stock.name,
        timestamp: stat.timestamp,
        price: stat.price,
        change: stat.change,
        changePercent: stat.changePercent,
        volume: stat.volume,
        pricePoints: sparkline.points,
      },
    ];
  });
}
