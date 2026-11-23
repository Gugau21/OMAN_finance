import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '../AppShell';
import { MainPage } from '../../pages/MainPage';
import { StockDetailsPage } from '../../pages/StockDetailsPage';

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<MainPage />} />
        <Route path="stocks/:symbol" element={<StockDetailsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
