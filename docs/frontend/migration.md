# OMAN Finance frontend migration

## Goal

Move the current frontend from page-owned fetching and state toward the
feature-based architecture described in
[`architecture.md`](./architecture.md).

Preserve the existing market behavior during every migration step.

## Current files to migrate

| Current file                     | Target                                        |
| -------------------------------- | --------------------------------------------- |
| `src/App.tsx`                    | `app/App.tsx` and router/app shell            |
| `src/pages/MainPage.tsx`         | `features/stocks/pages/StocksPage.tsx`        |
| `src/pages/StockDetailsPage.tsx` | `features/stocks/pages/StockDetailsPage.tsx`  |
| `src/components/Header.tsx`      | `components/layouts/AppLayout.tsx`            |
| `src/hooks/useMarketStatus.ts`   | `features/market/hooks/useMarketStatus.ts`    |
| `src/data/*.ts`                  | Feature API adapters or shared infrastructure |
| `src/mappers/*.ts`               | `features/stocks/mappers`                     |
| `src/types/*.ts`                 | Feature domain models                         |
| `src/data/dataSource.ts`         | API source configuration                      |
| `src/data/responseValidation.ts` | Feature/API validators                        |

The mock/real data switch belongs behind the API adapter. It must not be
imported by pages or visual components.

Market status is now organized as:

```text
src/features/market/
  api/marketApi.ts
  model/market.ts
  hooks/useMarketStatus.ts
  mappers/marketMapper.ts
  tests/marketApi.test.ts
```

The market model uses `Date` values after transport validation. Other feature
models should make the same conversion when a domain operation needs reliable
date arithmetic; raw API DTOs should remain boundary data.

The stock list is now organized as:

```text
src/features/stocks/
  api/stocksApi.ts
  model/stock.ts
  hooks/useStockList.ts
  mappers/stockListMapper.ts
  tests/stockListMapper.test.ts
```

`StocksPage` reads `useStockList()` and no longer owns an effect, an abort
controller, loading state, or stock-list fetching state.

## Migration plan

1. Add React Router and the application shell.
2. Add the HTTP client and TanStack Query provider.
3. Add the shared STOMP connection.
4. Migrate market status.
5. Migrate the stock list and three stock list snapshots.
6. Migrate stock details and charts.
7. Extract reusable UI primitives and chart components.
8. Remove direct API fetching and STOMP creation from pages.
9. Add generated REST types and runtime STOMP validators.

Preserve current behavior at each step and verify each migration with the
smallest relevant test suite.

## Acceptance criteria

- Stock and detail routes use React Router.
- Pages do not import fetch or STOMP implementation modules.
- API DTO validation happens before data enters the Query Cache.
- The mock/real source remains selectable without UI changes.
- Live updates follow the reconnect and timestamp merge rules.
- Existing market behavior remains available throughout migration.
- Type checking, linting, tests, and production build pass after each stage.
