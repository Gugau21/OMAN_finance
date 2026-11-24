import { BrowserRouter } from 'react-router-dom';
import { AppRouter } from './router/AppRouter';
import { QueryProvider } from './providers/QueryProvider';

export default function App() {
  return (
    <QueryProvider>
      <BrowserRouter>
        <AppRouter />
      </BrowserRouter>
    </QueryProvider>
  );
}
