import { useEffect, useState } from 'react';
import { getStocks } from '../data/stocksData';
import { getStockStats } from '../data/stockStatsData';
import { getSparklines } from '../data/sparklinesData';
import type { PricePoint } from '../types/stock';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts';

type StockDetails = {
  symbol: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  chartData: Array<{
    timestamp: string;
    price: number;
  }>;
};

type StockDetailsPageProps = {
  symbol: string;
  onBack: () => void;
};

export function StockDetailsPage({ symbol, onBack }: StockDetailsPageProps) {
  const [stockDetails, setStockDetails] = useState<StockDetails | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    async function loadStockDetails() {
      try {
        setIsLoading(true);
        setError(null);

        const [stocks, stats, sparklines] = await Promise.all([
          getStocks({ signal: controller.signal }),
          getStockStats({ signal: controller.signal }),
          getSparklines({ signal: controller.signal }),
        ]);

        const stock = stocks?.find((s) => s.symbol === symbol);
        const stat = stats?.find((s) => s.symbol === symbol);
        const spark = sparklines?.find((s) => s.symbol === symbol);

        if (stock && stat && spark) {
          const chartData = spark.points.map((pt: PricePoint) => {
            return {
              timestamp: new Date(pt.timestamp).toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit',
              }),
              price: pt.price,
            };
          });

          setStockDetails({
            symbol: stock.symbol,
            name: stock.name,
            price: stat.price,
            change: stat.change,
            changePercent: stat.changePercent,
            volume: stat.volume,
            chartData,
          });
        } else {
          setError(`Could not find data for ${symbol}.`);
        }
      } catch (err: unknown) {
        if (err instanceof Error && err.name !== 'AbortError') {
          setError('Failed to load stock details. Please try again.');
        } else if (!(err instanceof Error)) {
          setError('An unexpected error occurred.');
        }
      } finally {
        setIsLoading(false);
      }
    }

    loadStockDetails();
    return () => controller.abort();
  }, [symbol]);

  if (isLoading) {
    return (
      <div className="w-full max-w-7xl mx-auto px-2 py-3 bg-white min-h-screen">
        <div className="w-12 h-2 bg-gray-200 rounded animate-pulse mb-4"></div>
        <div className="w-24 h-4 bg-gray-200 rounded animate-pulse mb-1"></div>
        <div className="w-16 h-2 bg-gray-100 rounded animate-pulse mb-4"></div>
        <div className="w-full h-48 bg-gray-50 rounded animate-pulse mb-4"></div>
      </div>
    );
  }

  if (error || !stockDetails) {
    return (
      <div className="w-full max-w-7xl mx-auto px-2 py-3 bg-white min-h-screen">
        <button
          onClick={onBack}
          className="text-[10px] text-purple-700 hover:underline mb-4 font-inter"
        >
          &larr; Back to Markets
        </button>
        <div className="p-3 bg-red-50 text-red-600 rounded-lg border border-red-100 text-[10px]">
          {error || 'Stock not found'}
        </div>
      </div>
    );
  }

  const isPositive = stockDetails.change >= 0;
  const colorClass = isPositive ? 'text-green-600' : 'text-red-500';
  const strokeColor = isPositive ? '#16a34a' : '#ef4444';

  return (
    <div className="w-full max-w-7xl mx-auto px-2 pt-2 pb-4 flex flex-col gap-3 bg-white min-h-screen font-inter">
      {/* Navigation */}
      <div>
        <button
          onClick={onBack}
          className="text-[10px] font-medium text-gray-500 hover:text-purple-700 transition-colors"
        >
          &larr; Back to Markets
        </button>
      </div>

      {/* Header Info */}
      <div className="flex flex-row justify-between items-end border-b border-gray-200 pb-3">
        <div className="flex flex-col gap-0.5">
          <h1 className="text-lg font-bold text-gray-900">{stockDetails.name}</h1>
          <span className="text-[10px] font-medium text-gray-500">{stockDetails.symbol}</span>
        </div>
        <div className="flex flex-col items-end gap-0.5">
          <span className="text-lg font-bold text-gray-900">${stockDetails.price.toFixed(2)}</span>
          <div className={`flex flex-row gap-1 text-[10px] font-bold ${colorClass}`}>
            <span>
              {isPositive ? '+' : ''}
              {stockDetails.change.toFixed(2)}
            </span>
            <span>
              ({isPositive ? '+' : ''}
              {stockDetails.changePercent.toFixed(2)}%)
            </span>
          </div>
        </div>
      </div>

      {/* Main Chart */}
      <div className="w-full h-48 mt-2">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart
            data={stockDetails.chartData}
            margin={{ top: 5, right: 5, left: -25, bottom: 0 }}
          >
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f3f4f6" />
            <XAxis
              dataKey="timestamp"
              axisLine={false}
              tickLine={false}
              tick={{ fontSize: 9, fill: '#6b7280' }}
              minTickGap={30}
            />
            <YAxis
              domain={['auto', 'auto']}
              axisLine={false}
              tickLine={false}
              tick={{ fontSize: 9, fill: '#6b7280' }}
              tickFormatter={(val) => `$${val}`}
            />
            <Tooltip
              contentStyle={{
                borderRadius: '6px',
                border: 'none',
                padding: '6px',
                boxShadow: '0 2px 4px -1px rgb(0 0 0 / 0.1)',
              }}
              labelStyle={{ color: '#6b7280', fontSize: '10px', marginBottom: '2px' }}
              itemStyle={{ color: '#111827', fontSize: '12px', fontWeight: 'bold' }}
            />
            <Line
              type="monotone"
              dataKey="price"
              stroke={strokeColor}
              strokeWidth={1.5}
              dot={false}
              activeDot={{ r: 3, fill: strokeColor, stroke: '#fff', strokeWidth: 1.5 }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
