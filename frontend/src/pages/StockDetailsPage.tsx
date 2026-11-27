import { useMemo, useState } from 'react';
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts';
import { useNavigate, useParams } from 'react-router-dom';
import { useStockDetail } from '../features/stock_detail/hooks/useStockDetail';
import type { StockDetail } from '../features/stock_detail/model/stock_detail';

type CandlestickShapeProps = {
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  payload?: {
    open: number;
    close: number;
    high: number;
    low: number;
  };
};

type FormattedCandle = {
  timestamp: string;
  wick: number[];
  open: number;
  close: number;
  high: number;
  low: number;
  color: string;
};

const CandlestickShape = (props: CandlestickShapeProps) => {
  const { x, y, width, height, payload } = props;

  if (
    payload === undefined ||
    x === undefined ||
    y === undefined ||
    width === undefined ||
    height === undefined
  ) {
    return null;
  }

  const { open, close, high, low } = payload;
  const isGrowing = close >= open;
  const color = isGrowing ? '#16a34a' : '#ef4444';

  const valueRange = high - low;
  let bodyY = y;
  let bodyHeight = height;

  if (valueRange > 0) {
    const ratio = height / valueRange;
    const topBodyValue = Math.max(open, close);
    bodyY = y + (high - topBodyValue) * ratio;
    bodyHeight = Math.abs(open - close) * ratio;
  }

  const centerX = x + width / 2;

  return (
    <g stroke={color} fill={color} strokeWidth="1.5">
      <line x1={centerX} y1={y} x2={centerX} y2={y + height} />

      <rect x={x} y={bodyY} width={width} height={Math.max(bodyHeight, 1)} />
    </g>
  );
};

export function StockDetailsPage() {
  const navigate = useNavigate();
  const { symbol } = useParams<{ symbol: string }>();
  const activeSymbol = symbol ?? '';
  const {
    data: stockDetails,
    isLoading,
    error,
  } = useStockDetail(activeSymbol);

  const [chartType, setChartType] = useState<'line' | 'candles'>('line');

  const { lineChartData, candleChartData } = useMemo(() => {
    if (!stockDetails) {
      return {
        lineChartData: [] as Array<{ timestamp: string; price: number }>,
        candleChartData: [] as FormattedCandle[],
      };
    }

const points = Array.isArray(stockDetails.sparkline)
  ? stockDetails.sparkline
  : stockDetails.sparkline.points; // or .items / .series / .values

const lineData = points.map((pt) => ({
  timestamp: new Date(pt.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  }),
  price: pt.price,
}));

    const candleData = stockDetails.candles.candles.map((pt) => {
      const isGrowing = pt.close >= pt.open;
      return {
        timestamp: pt.timestamp.toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
        }),
        wick: [pt.low, pt.high],
        open: pt.open,
        close: pt.close,
        high: pt.high,
        low: pt.low,
        color: isGrowing ? '#16a34a' : '#ef4444',
      };
    });

    return { lineChartData: lineData, candleChartData: candleData };
  }, [stockDetails]);

  if (isLoading) {
    return (
      <div className="w-full max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-8 bg-white min-h-screen">
        <div className="w-12 h-2 bg-gray-200 rounded animate-pulse mb-4"></div>
        <div className="w-24 h-4 bg-gray-200 rounded animate-pulse mb-1"></div>
        <div className="w-16 h-2 bg-gray-100 rounded animate-pulse mb-4"></div>
        <div className="w-full h-48 bg-gray-50 rounded animate-pulse mb-4"></div>
      </div>
    );
  }

  const errorMessage =
    error instanceof Error
      ? error.message || 'Failed to load stock details. Please try again.'
      : error
        ? 'An unexpected error occurred.'
        : null;

  if (errorMessage || !stockDetails) {
    return (
      <div className="w-full max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-8 bg-white min-h-screen">
        <button
          onClick={() => navigate('/')}
          className="text-[10px] text-purple-700 hover:underline mb-4 font-inter"
        >
          &larr; Back to Markets
        </button>
        <div className="p-3 bg-red-50 text-red-600 rounded-lg border border-red-100 text-[10px]">
          {errorMessage || (symbol ? `Could not find data for ${symbol}.` : 'Stock not found')}
        </div>
      </div>
    );
  }

  const detail: StockDetail = stockDetails;
  const isPositive = detail.stat.change >= 0;
  const colorClass = isPositive ? 'text-green-600' : 'text-red-500';
  const strokeColor = isPositive ? '#16a34a' : '#ef4444';

  return (
    <div className="w-full max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-8 flex flex-col gap-3 bg-white min-h-screen font-inter">
      <div>
        <button
          onClick={() => navigate('/')}
          className="text-[10px] font-medium text-gray-500 hover:text-purple-700 transition-colors"
        >
          &larr; Back to Markets
        </button>
      </div>

      <div className="flex flex-row justify-between items-end border-b border-gray-200 pb-3">
        <div className="flex flex-col gap-0.5">
          <h1 className="text-lg font-bold text-gray-900">{symbol}</h1>
          <span className="text-[10px] font-medium text-gray-500">{symbol}</span>
        </div>
        <div className="flex flex-col items-end gap-0.5">
          <span className="text-lg font-bold text-gray-900">
            ${detail.stat.price.toFixed(2)}
          </span>
          <div className={`flex flex-row gap-1 text-[10px] font-bold ${colorClass}`}>
            <span>
              {isPositive ? '+' : ''}
              {detail.stat.change.toFixed(2)}
            </span>
            <span>
              ({isPositive ? '+' : ''}
              {detail.stat.changePercent.toFixed(2)}%)
            </span>
          </div>
        </div>
      </div>

      <div className="flex flex-row gap-2 mt-2">
        <button
          onClick={() => setChartType('line')}
          className={`px-3 py-1 text-xs font-medium rounded-full transition-colors ${
            chartType === 'line'
              ? 'bg-purple-100 text-purple-800'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }`}
        >
          Line
        </button>
        <button
          onClick={() => setChartType('candles')}
          className={`px-3 py-1 text-xs font-medium rounded-full transition-colors ${
            chartType === 'candles'
              ? 'bg-purple-100 text-purple-800'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }`}
        >
          Candles
        </button>
      </div>

      <div className="w-full h-56 mt-2">
        <ResponsiveContainer width="100%" height="100%">
          {chartType === 'line' ? (
            <LineChart data={lineChartData} margin={{ top: 5, right: 5, left: -25, bottom: 0 }}>
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
                tickFormatter={(val) => `$${val.toFixed(2)}`}
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
          ) : (
            <BarChart data={candleChartData} margin={{ top: 5, right: 5, left: -25, bottom: 0 }}>
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
                tickFormatter={(val) => `$${val.toFixed(0)}`}
              />
              <Tooltip
                contentStyle={{
                  borderRadius: '6px',
                  border: 'none',
                  padding: '8px',
                  boxShadow: '0 2px 4px -1px rgb(0 0 0 / 0.1)',
                }}
                labelStyle={{ color: '#6b7280', fontSize: '10px', marginBottom: '4px' }}
                cursor={{ fill: '#f3f4f6' }}
                content={({ active, payload, label }) => {
                  if (active && payload && payload.length) {
                    const data = payload[0].payload;
                    return (
                      <div className="bg-white p-2 shadow-sm rounded text-xs font-inter border border-gray-100">
                        <p className="text-gray-500 mb-1">{label}</p>
                        <p>
                          <span className="text-gray-500 w-8 inline-block">O:</span>{' '}
                          <span className="font-semibold text-gray-900">
                            ${data.open.toFixed(2)}
                          </span>
                        </p>
                        <p>
                          <span className="text-gray-500 w-8 inline-block">H:</span>{' '}
                          <span className="font-semibold text-gray-900">
                            ${data.high.toFixed(2)}
                          </span>
                        </p>
                        <p>
                          <span className="text-gray-500 w-8 inline-block">L:</span>{' '}
                          <span className="font-semibold text-gray-900">
                            ${data.low.toFixed(2)}
                          </span>
                        </p>
                        <p>
                          <span className="text-gray-500 w-8 inline-block">C:</span>{' '}
                          <span className="font-semibold text-gray-900">
                            ${data.close.toFixed(2)}
                          </span>
                        </p>
                      </div>
                    );
                  }
                  return null;
                }}
              />
              <Bar dataKey="wick" shape={<CandlestickShape />} isAnimationActive={false} />
            </BarChart>
          )}
        </ResponsiveContainer>
      </div>
    </div>
  );
}