# React + TypeScript + Vite

The frontend uses React with TypeScript, Vite, ESLint, and Vitest.

## API data sources

The data functions support mock and real backend data independently. Copy
`.env.example` to `.env.local` and configure:

```env
VITE_DATA_SOURCE=real
VITE_MOCK_MARKET_STATUS=false
VITE_MOCK_STOCKS=true
```

In this example, market status comes from the backend while the stock list
continues to use mock data. Restart the Vite server after changing environment
variables.

## Development

```bash
npm install
npm run dev
```

Useful checks:

- `npm run typecheck`
- `npm run lint`
- `npm test`
- `npm run build`
