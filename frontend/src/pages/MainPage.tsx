import { useEffect, useState } from 'react';
import { getMarketStatus } from '../data/marketData';
import { getStocks } from '../data/stocksData';
import { getStockStats } from '../data/stockStatsData';
import { getSparklines } from '../data/sparklinesData';
import type { MarketStatus } from '../types/market';
import type { PricePoint } from '../types/stock';
import { LineChart, Line, YAxis, ResponsiveContainer } from 'recharts';

type CombinedStockData = {
  symbol: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  sparkline: PricePoint[];
};

export function MainPage() {
  const [marketStatus, setMarketStatus] = useState<MarketStatus | null>(null);
  const [marketDate, setMarketDate] = useState<string>('');
  const [stocksData, setStocksData] = useState<CombinedStockData[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();

    async function loadDashboardData() {
      try {
        const [status, stocks, stats, sparklines] = await Promise.all([
          getMarketStatus({ signal: controller.signal }),
          getStocks({ signal: controller.signal }),
          getStockStats({ signal: controller.signal }),
          getSparklines({ signal: controller.signal }),
        ]);

        if (status) {
          setMarketStatus(status);
          const dateObj = new Date(status.timestamp);
          setMarketDate(
            dateObj.toLocaleDateString('en-US', {
              weekday: 'long',
              month: 'long',
              day: 'numeric',
              year: 'numeric',
            }),
          );
        }

        if (stocks && stats && sparklines) {
          const combined = stocks.map((stock) => {
            const stat = stats.find((s) => s.symbol === stock.symbol);
            const spark = sparklines.find((s) => s.symbol === stock.symbol);

            return {
              symbol: stock.symbol,
              name: stock.name,
              price: stat?.price || 0,
              change: stat?.change || 0,
              changePercent: stat?.changePercent || 0,
              volume: stat?.volume || 0,
              sparkline: spark?.points || [],
            };
          });
          setStocksData(combined);
        }
      } catch (error: unknown) {
        if (error instanceof Error && error.name !== 'AbortError') {
          console.error('Failed to fetch dashboard data:', error.message);
        } else if (!(error instanceof Error)) {
          console.error('Failed to fetch dashboard data:', error);
        }
      } finally {
        setIsLoading(false);
      }
    }

    loadDashboardData();
    return () => controller.abort();
  }, []);

  const formatVolume = (vol: number) => {
    if (vol >= 1e6) return (vol / 1e6).toFixed(1) + 'M';
    if (vol >= 1e3) return (vol / 1e3).toFixed(1) + 'K';
    return vol.toString();
  };

  return (
    <div className="w-full max-w-7xl mx-auto px-1 pt-1 pb-4 flex flex-col gap-4 bg-white min-h-screen">
      <div className="w-full flex flex-row justify-between items-center pb-4">
        <h1 className="text-base font-bold text-gray-900 font-inter">The market, at a glance.</h1>

        <div className="flex flex-col items-end">
          <div className="flex flex-row items-center gap-1.5">
            <div
              className={`w-1 h-1 rounded-full ${marketStatus?.isOpen ? 'bg-green-500' : 'bg-slate-500'}`}
            ></div>
            <span className="text-[10px] font-bold text-gray-800 font-inter">
              {marketStatus?.isOpen ? 'US markets open' : 'US markets closed'}
            </span>
          </div>
          <span className="text-[10px] text-gray-500 font-inter">{marketDate || 'Loading...'}</span>
        </div>
      </div>

      <div className="w-full flex flex-col gap-2">
        <h2 className="text-xs font-bold text-gray-900">Explore stocks</h2>

        {isLoading ? (
          <div className="text-gray-500 py-2 text-xs">Loading market data...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-gray-200 text-gray-500 text-[10px] bg-gray-100">
                  <th className="py-3 px-2 font-normal">Company</th>
                  <th className="py-3 px-2 font-normal text-right">Price</th>
                  <th className="py-3 px-2 font-normal text-right">Change</th>
                  <th className="py-3 px-2 font-normal text-right">Change %</th>
                  <th className="py-3 px-2 font-normal text-right">Volume</th>
                  <th className="py-3 px-2 font-normal text-right">7-day trend</th>
                </tr>
              </thead>
              <tbody className="text-xs">
                {stocksData.map((row) => {
                  const isPositive = row.change >= 0;
                  const colorClass = isPositive ? 'text-green-600' : 'text-red-500';
                  const strokeColor = isPositive ? '#16a34a' : '#ef4444';

                  return (
                    <tr
                      key={row.symbol}
                      className="border-b border-gray-100 hover:bg-purple-50 transition-colors"
                    >
                      <td className="py-3 px-2">
                        <div className="flex flex-col items-start gap-1">
                          <button className="text-purple-700 font-bold text-xs hover:underline focus:outline-none">
                            {row.symbol}
                          </button>
                          <span className="text-gray-500 text-[9px]">{row.name}</span>
                        </div>
                      </td>
                      <td className="py-3 px-2 text-right font-bold text-gray-900">
                        {row.price.toFixed(2)}
                      </td>
                      <td className={`py-3 px-2 text-right font-medium ${colorClass}`}>
                        {isPositive ? '+' : ''}
                        {row.change.toFixed(2)}
                      </td>
                      <td className={`py-3 px-2 text-right font-bold ${colorClass}`}>
                        {isPositive ? '+' : ''}
                        {row.changePercent.toFixed(2)}%
                      </td>
                      <td className="py-3 px-2 text-right text-gray-700">
                        {formatVolume(row.volume)}
                      </td>
                      <td className="py-3 px-2 h-10 w-20">
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={row.sparkline}>
                            <YAxis domain={['dataMin', 'dataMax']} hide />
                            <Line
                              type="monotone"
                              dataKey="price"
                              stroke={strokeColor}
                              strokeWidth={1.5}
                              dot={false}
                              isAnimationActive={false}
                            />
                          </LineChart>
                        </ResponsiveContainer>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
