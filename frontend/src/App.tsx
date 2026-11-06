import { Header } from './components/Header';
import { MainPage } from './pages/MainPage';

export default function App() {
  return (
    <div className=" mx-auto p-4 flex flex-col gap-4 bg-white min-h-screen">
      <Header />
      <MainPage />
    </div>
  );
}
