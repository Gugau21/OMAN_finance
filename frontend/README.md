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

## Cancelling requests

`AbortController` provides controlled cancellation. Calling `abort()` does not
resolve the request with data; it causes the promise to reject so the caller
can stop processing the result. Treat an abort as an intentional cancellation,
not as a user-visible error:

```ts
try {
  const status = await getMarketStatus({ signal: controller.signal });
  setStatus(status);
} catch (error) {
  if (error instanceof DOMException && error.name === 'AbortError') {
    return;
  }

  setError(error);
}
```

Use this when a component unmounts or starts a newer request. Only non-abort
errors should switch the UI to its error state.

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
