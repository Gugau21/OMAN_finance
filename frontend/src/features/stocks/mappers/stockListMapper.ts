import type { StockListItem, StockStat } from '../model/stock';
import type {
  ListSparklinesResponse,
  ListStockStatsResponse,
  ListStocksResponse,
} from '../api/StockApiDto';
import type { SparklinePointMessageDto, StockStatMessageDto } from '../realtime/stockMessageParser';

export function toStockListItem(
  stocks: ListStocksResponse,
  stats: ListStockStatsResponse,
  sparklines: ListSparklinesResponse,
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
        stat: {
          price: stat.price,
          change: stat.change,
          changePercent: stat.changePercent,
          volume: stat.volume,
          timestamp: new Date(stat.timestamp),
        },
        sparkline: {
          points: sparkline.points.map((point) => ({
            ...point,
            timestamp: new Date(point.timestamp),
          })),
        },
      },
    ];
  });
}

export function mergeStockStatMessage(
  stocks: StockListItem[] | null,
  message: StockStatMessageDto,
): StockListItem[] | null {
  if (!stocks) {
    return stocks;
  }

  const timestamp = new Date(message.timestamp);

  return stocks.map((stock) => {
    if (stock.symbol !== message.symbol || timestamp.getTime() < stock.stat.timestamp.getTime()) {
      return stock;
    }

    const stat: StockStat = {
      timestamp,
      price: message.price,
      change: message.change,
      changePercent: message.changePercent,
      volume: message.volume,
    };

    return { ...stock, stat };
  });
}

export function mergeSparklinePointMessage(
  stocks: StockListItem[] | null,
  message: SparklinePointMessageDto,
): StockListItem[] | null {
  if (!stocks) {
    return stocks;
  }

  const timestamp = new Date(message.timestamp);

  return stocks.map((stock) => {
    if (stock.symbol !== message.symbol) {
      return stock;
    }

    const points = stock.sparkline.points;
    const lastTimestamp = points[points.length - 1]?.timestamp.getTime();
    if (lastTimestamp !== undefined && timestamp.getTime() < lastTimestamp) {
      return stock;
    }

    const updatedPoints = points
      .filter((point) => point.timestamp.getTime() !== timestamp.getTime())
      .concat({ timestamp, price: message.price })
      .sort((left, right) => left.timestamp.getTime() - right.timestamp.getTime())
      .slice(-60);

    return {
      ...stock,
      sparkline: { points: updatedPoints },
    };
  });
}
