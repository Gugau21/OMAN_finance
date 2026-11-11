import { useEffect, useState } from 'react';
import { getMarketStatus } from '../data/marketData';
import { getMainPageStocks } from '../data/mainPageData';
import type { MarketStatus } from '../types/market';
import type { MainPageStock } from '../types/stock';
import { LineChart, Line, YAxis, ResponsiveContainer } from 'recharts';

type MainPageProps = {
  onStockSelect: (symbol: string) => void;
};

export function MainPage({ onStockSelect }: MainPageProps) {
  // 2. Use the proper MarketStatus type here again
  const [marketStatus, setMarketStatus] = useState<MarketStatus | null>(null);
  const [marketDate, setMarketDate] = useState<string>('');
  const [stocksData, setStocksData] = useState<MainPageStock[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    async function loadDashboardData() {
      try {
        setIsLoading(true);
        setError(null);

        // 3. Add 'as const' to strictly type the Promise array
        const [status, combinedStocks] = await Promise.all([
          getMarketStatus({ signal: controller.signal }),
          getMainPageStocks(controller.signal),
        ] as const);

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

        if (combinedStocks) {
          setStocksData(combinedStocks);
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
    <div className="w-full max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-8 flex flex-col gap-6 bg-white min-h-screen">
      <div className="w-full flex flex-row justify-between items-center pb-4">
        <h1 className="text-xl font-bold text-gray-900 font-inter">The market, at a glance.</h1>

        <div className="flex flex-col items-end">
          <div className="flex flex-row items-center gap-1.5">
            <div
              className={`w-1.5 h-1.5 rounded-full ${
                isLoading
                  ? 'bg-gray-300 animate-pulse'
                  : marketStatus?.isOpen
                    ? 'bg-green-500'
                    : 'bg-slate-500'
              }`}
            ></div>
            <span className="text-xs font-bold text-gray-800 font-inter">
              {isLoading
                ? 'Loading status...'
                : marketStatus?.isOpen
                  ? 'US markets open'
                  : 'US markets closed'}
            </span>
          </div>
          <span className="text-xs text-gray-500 font-inter mt-0.5">
            {isLoading ? (
              <span className="inline-block w-24 h-3 bg-gray-200 rounded animate-pulse mt-1"></span>
            ) : (
              marketDate || 'Date unavailable'
            )}
          </span>
        </div>
      </div>

      <div className="w-full flex flex-col gap-3">
        <h2 className="text-sm font-bold text-gray-900">Explore stocks</h2>

        <div className="overflow-x-auto shadow-sm  ring-opacity-5 rounded-lg">
          <table className="min-w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-gray-200 text-gray-500 text-xs bg-gray-50">
                <th className="py-3 px-4 font-medium">Company</th>
                <th className="py-3 px-4 font-medium text-right">Price</th>
                <th className="py-3 px-4 font-medium text-right">Change</th>
                <th className="py-3 px-4 font-medium text-right">Change %</th>
                <th className="py-3 px-4 font-medium text-right">Volume</th>
                <th className="py-3 px-4 font-medium text-right">1-hour trend</th>
              </tr>
            </thead>

            {isLoading && (
              <tbody className="text-sm bg-white">
                {[1, 2, 3, 4].map((item) => (
                  <tr key={item} className="border-b border-gray-100">
                    <td className="py-4 px-4">
                      <div className="flex flex-col gap-1.5">
                        <div className="w-12 h-4 bg-gray-200 rounded animate-pulse"></div>
                        <div className="w-24 h-3 bg-gray-100 rounded animate-pulse"></div>
                      </div>
                    </td>
                    <td className="py-4 px-4">
                      <div className="w-16 h-4 bg-gray-200 rounded animate-pulse ml-auto"></div>
                    </td>
                    <td className="py-4 px-4">
                      <div className="w-12 h-4 bg-gray-200 rounded animate-pulse ml-auto"></div>
                    </td>
                    <td className="py-4 px-4">
                      <div className="w-14 h-4 bg-gray-200 rounded animate-pulse ml-auto"></div>
                    </td>
                    <td className="py-4 px-4">
                      <div className="w-12 h-4 bg-gray-200 rounded animate-pulse ml-auto"></div>
                    </td>
                    <td className="py-4 px-4">
                      <div className="w-20 h-8 bg-gray-100 rounded animate-pulse ml-auto"></div>
                    </td>
                  </tr>
                ))}
              </tbody>
            )}

            {!isLoading && error && (
              <tbody className="bg-white">
                <tr>
                  <td colSpan={6} className="py-12 px-4">
                    <div className="flex flex-col items-center justify-center text-center bg-red-50 rounded-lg p-8 border border-red-100 max-w-lg mx-auto">
                      <span className="text-red-600 font-bold text-sm mb-2">Data Fetch Failed</span>
                      <span className="text-red-500 text-xs">{error}</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            )}

            {!isLoading && !error && stocksData.length === 0 && (
              <tbody className="bg-white">
                <tr>
                  <td colSpan={6} className="py-16 px-4">
                    <div className="flex flex-col items-center justify-center text-center">
                      <span className="text-gray-900 font-medium text-sm mb-1">
                        No Stocks Available
                      </span>
                      <span className="text-gray-500 text-xs">
                        The market data is currently empty.
                      </span>
                    </div>
                  </td>
                </tr>
              </tbody>
            )}

            {!isLoading && !error && stocksData.length > 0 && (
              <tbody className="text-sm bg-white">
                {stocksData.map((row) => {
                  const isPositive = row.change >= 0;
                  const colorClass = isPositive ? 'text-green-600' : 'text-red-500';
                  const strokeColor = isPositive ? '#16a34a' : '#ef4444';

                  return (
                    <tr
                      key={row.symbol}
                      className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                    >
                      <td className="py-4 px-4">
                        <div className="flex flex-col items-start gap-1">
                          <button
                            onClick={() => onStockSelect(row.symbol)}
                            className="text-purple-700 font-bold text-sm hover:text-purple-900 hover:underline focus:outline-none transition-colors"
                          >
                            {row.symbol}
                          </button>
                          <span className="text-gray-500 text-xs">{row.name}</span>
                        </div>
                      </td>
                      <td className="py-4 px-4 text-right font-medium text-gray-900">
                        ${row.price.toFixed(2)}
                      </td>
                      <td className={`py-4 px-4 text-right font-medium ${colorClass}`}>
                        {isPositive ? '+' : ''}
                        {row.change.toFixed(2)}
                      </td>
                      <td className={`py-4 px-4 text-right font-semibold ${colorClass}`}>
                        {isPositive ? '+' : ''}
                        {row.changePercent.toFixed(2)}%
                      </td>
                      <td className="py-4 px-4 text-right text-gray-600">
                        {formatVolume(row.volume)}
                      </td>
                      <td className="py-4 px-4 h-12 w-24">
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={row.pricePoints}>
                            <YAxis domain={['dataMin', 'dataMax']} hide />
                            <Line
                              type="monotone"
                              dataKey="price"
                              stroke={strokeColor}
                              strokeWidth={2}
                              dot={false}
                              activeDot={false}
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
