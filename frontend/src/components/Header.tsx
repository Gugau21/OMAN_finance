export function Header() {
  return (
    <header className="flex  flex-col items-right md:justify-between gap-5 border-b border-gray-300 pb-2 md:flex-row">
      <div className="flex flex-row items-center gap-1">
        <img className="size-8" src="src/assets/AppIcon.png" alt="App Icon" />
        <h1 className="text-xl font-bold text-violet-600 font-inter">OMAN</h1>
        <p className="text-sm text-gray-500 font-inter">finance</p>
      </div>

      <div className="flex flex-row items-center gap-2">
        <button className="text-xs text-violet-600 hover:underline cursor-pointer font-inter font-semibold">
          Markets
        </button>
      </div>
    </header>
  );
}
