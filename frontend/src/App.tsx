import { useState } from 'react';
import { Header } from './components/Header';
import { MainPage } from './pages/MainPage';
import { StockDetailsPage } from './pages/StockDetailsPage';

export default function App() {
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);

  return (
    <div className="mx-auto p-4 flex flex-col gap-4 bg-white min-h-screen">
      <Header />
      {selectedSymbol ? (
        <StockDetailsPage symbol={selectedSymbol} onBack={() => setSelectedSymbol(null)} />
      ) : (
        <MainPage onStockSelect={setSelectedSymbol} />
      )}
    </div>
  );
}
