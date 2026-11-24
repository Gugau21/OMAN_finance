import type { Sparkline, Stock, StockStat } from '../model/stock';
import type { StockListItem } from '../model/stock';

export function createStockList(
  stocks: Stock[],
  stats: StockStat[],
  sparklines: Sparkline[],
): StockListItem[] {
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
