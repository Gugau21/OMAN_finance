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
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    async function loadDashboardData() {
      try {
        setIsLoading(true);
        setError(null);

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
        } else {
          setStocksData([]);
        }
      } catch (err: unknown) {
        if (err instanceof Error && err.name !== 'AbortError') {
          console.error('Failed to fetch dashboard data:', err.message);
          setError('We encountered an issue loading the market data. Please try again later.');
        } else if (!(err instanceof Error)) {
          console.error('Failed to fetch dashboard data:', err);
          setError('An unexpected error occurred.');
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
              className={`w-1 h-1 rounded-full ${
                isLoading
                  ? 'bg-gray-300 animate-pulse'
                  : marketStatus?.isOpen
                    ? 'bg-green-500'
                    : 'bg-slate-500'
              }`}
            ></div>
            <span className="text-[10px] font-bold text-gray-800 font-inter">
              {isLoading
                ? 'Loading status...'
                : marketStatus?.isOpen
                  ? 'US markets open'
                  : 'US markets closed'}
            </span>
          </div>
          <span className="text-[10px] text-gray-500 font-inter">
            {isLoading ? (
              <span className="inline-block w-24 h-3 bg-gray-200 rounded animate-pulse mt-1"></span>
            ) : (
              marketDate || 'Date unavailable'
            )}
          </span>
        </div>
      </div>

      <div className="w-full flex flex-col gap-2">
        <h2 className="text-xs font-bold text-gray-900">Explore stocks</h2>

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
            {/*Display loading skeletons when data is being fetched */}
            {isLoading && (
              <tbody className="text-xs">
                {[1, 2, 3].map((item) => (
                  <tr key={item} className="border-b border-gray-100">
                    <td className="py-3 px-2">
                      <div className="flex flex-col gap-1">
                        <div className="w-10 h-4 bg-gray-200 rounded animate-pulse"></div>
                        <div className="w-20 h-3 bg-gray-100 rounded animate-pulse"></div>
                      </div>
                    </td>
                    <td className="py-3 px-2">
                      <div className="w-12 h-4 bg-gray-200 rounded animate-pulse ml-auto"></div>
                    </td>
                    <td className="py-3 px-2">
                      <div className="w-10 h-4 bg-gray-200 rounded animate-pulse ml-auto"></div>
                    </td>
                    <td className="py-3 px-2">
                      <div className="w-12 h-4 bg-gray-200 rounded animate-pulse ml-auto"></div>
                    </td>
                    <td className="py-3 px-2">
                      <div className="w-10 h-4 bg-gray-200 rounded animate-pulse ml-auto"></div>
                    </td>
                    <td className="py-3 px-2">
                      <div className="w-16 h-8 bg-gray-100 rounded animate-pulse ml-auto"></div>
                    </td>
                  </tr>
                ))}
              </tbody>
            )}

            {/*Display error message when data fetch fails */}
            {!isLoading && error && (
              <tbody>
                <tr>
                  <td colSpan={6} className="py-8">
                    <div className="flex flex-col items-center justify-center text-center bg-red-50 rounded-lg p-6 border border-red-100">
                      <span className="text-red-500 font-bold text-xs mb-1">Data Fetch Failed</span>
                      <span className="text-red-400 text-[10px]">{error}</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            )}

            {/*Display message when no stocks are available */}
            {!isLoading && !error && stocksData.length === 0 && (
              <tbody>
                <tr>
                  <td colSpan={6} className="py-12">
                    <div className="flex flex-col items-center justify-center text-center">
                      <span className="text-gray-500 font-bold text-xs mb-1">
                        No Stocks Available
                      </span>
                      <span className="text-gray-400 text-[10px]">
                        The market data is currently empty.
                      </span>
                    </div>
                  </td>
                </tr>
              </tbody>
            )}

            {/*Display stocks data when available */}
            {!isLoading && !error && stocksData.length > 0 && (
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
            )}
          </table>
        </div>
      </div>
    </div>
  );
}
