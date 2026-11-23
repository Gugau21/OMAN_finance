import { Outlet } from 'react-router-dom';
import { Header } from '../components/Header';

export function AppShell() {
  return (
    <div className="mx-auto flex min-h-screen flex-col gap-4 bg-white p-4">
      <Header />
      <Outlet />
    </div>
  );
}
