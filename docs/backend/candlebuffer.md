# CandleBuffer

Turns raw bars into candles and keeps the last 60 trading minutes of candles
per symbol complete and ready to serve.

Part of the [backend module overview](modular-architecture.md).

## Component diagram

Solid arrows show the direction data flows. Dashed arrows labeled "calls"
point from the caller to the component it calls. The red path is the live
bar, from `streamBars()` through `LiveCandleStream` to the `CandleBuffer`
interface. `CandleGapDetector` is not built yet, so it sits in the module
with no connections.

Hexagons are public interfaces. `MarketIngestion` appears only as its
interface. Other nodes in the module box are package-private, except the
`Candle` record, which the interface uses. Redis (`candles:{symbol}`) is the
only external system this module connects to.

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "fontFamily": "Inter, Segoe UI, Helvetica, Arial, sans-serif",
    "fontSize": "14px",
    "darkMode": true,
    "background": "#161616",
    "lineColor": "#a3a3a3",
    "textColor": "#e5e5e5",
    "primaryTextColor": "#e5e5e5",
    "clusterBkg": "#161616",
    "clusterBorder": "#525252",
    "edgeLabelBackground": "transparent"
  },
  "themeCSS": ".labelBkg, .edgeLabel, .edgeLabel p, .edgeLabel rect { background-color: transparent !important; fill: transparent !important; }",
  "flowchart": { "curve": "basis", "nodeSpacing": 60, "rankSpacing": 70, "padding": 16, "htmlLabels": true }
}}%%
flowchart TB
    ingestionApi{{"<i>«interface»</i><br/><b>MarketIngestion</b><br/><small>streamBars(): stream of Bars<br/>getBars(BarsRequest): BarsResponse<br/>getMarketStatus(): Clock<br/>streamMarketStatus(): stream of Clocks<br/>getTrackedStocks(): list of Stocks<br/>addSymbol(symbol)<br/>removeSymbol(symbol)</small>"}}

    subgraph buffer["<b>candlebuffer</b>"]
        direction TB
        subgraph bufferDetector[" "]
            detector["<b>CandleGapDetector</b><br/><small>reads stored candles, returns the gap or none:<br/>before a given candle<br/>or in the last n trading minutes</small>"]
        end
        subgraph bufferGap[" "]
            direction LR
            backfiller["<b>CandleBackfiller</b><br/><small>builds BarsRequest for a<br/>missing range (start, end, limit, sort)<br/>Candle.from(symbol, bar)<br/>saves and returns the candles</small>"]
            candleRepo["<b>CandleRedisRepository</b><br/><small>ZADD, ZREMRANGEBYRANK, ZRANGE</small>"]
        end
        subgraph bufferEntry[" "]
            direction LR
            candleHub["<b>LiveCandleStream</b><br/><small>consumes streamBars()<br/>Candle.from(symbol, bar)<br/>1. save candle<br/>2. gap: backfill<br/>3. emit backfilled candles, then the current one</small>"]
            history["<b>CandleHistory</b><br/><small>serves getLastCandles(symbol, n)<br/>gap: backfill<br/>no gap: read from Redis</small>"]
        end
        subgraph bufferIface[" "]
            direction LR
            bufferApi{{"<i>«interface»</i><br/><b>CandleBuffer</b><br/><small>streamCandles(): stream of Candles<br/>getLastCandles(symbol, n)</small>"}}
            candleRecord["<i>«record»</i><br/><b>Candle</b><br/><small>symbol, openTime, open, high, low, close, volume<br/>static from(symbol, Bar): Candle</small>"]
        end
    end

    redisCandles[("<b>Redis</b><br/><small>candles:{symbol}</small>")]

    %% Edge order is the linkStyle index. ~~~ counts.
    %% 0
    candleRecord ~~~ candleRepo
    %% 1
    candleHub -.->|"calls<br/>save candle"| candleRepo
    %% 2
    history -.->|"calls<br/>read last n"| candleRepo
    %% 3 live
    bufferApi <-->|"stream of Candles"| candleHub
    %% 4
    candleRepo -.->|"calls<br/>save: ZADD + trim to 60<br/>read: ZRANGE"| redisCandles
    %% 5
    ingestionApi -->|"getBars(request)"| backfiller
    %% 6
    bufferApi <-->|"last n candles"| history
    %% 7
    backfiller -.->|"calls<br/>save candles"| candleRepo
    %% 8 live
    ingestionApi -->|"streamBars()"| candleHub

    linkStyle 3 stroke:#ef4444,stroke-width:2px,marker-end:none
    linkStyle 8 stroke:#ef4444,stroke-width:2px
    linkStyle 6 marker-end:none

    classDef datastore  fill:#3f3f3f,stroke:#d4d4d4,stroke-width:1.5px,color:#f5f5f5
    classDef component  fill:#262626,stroke:#737373,stroke-width:1px,color:#e5e5e5
    classDef iface      fill:#262626,stroke:#d4d4d4,stroke-width:1.5px,stroke-dasharray:5 3,color:#f5f5f5
    classDef muted      fill:#1d1d1d,stroke:#484848,stroke-width:1px,color:#8a8a8a

    class redisCandles datastore
    class history,backfiller,candleHub,candleRecord,candleRepo component
    class detector muted
    class ingestionApi,bufferApi iface

    style buffer fill:#161616,stroke:#525252,stroke-width:1px,color:#e5e5e5
    style bufferDetector fill:none,stroke:none
    style bufferGap fill:none,stroke:none
    style bufferEntry fill:none,stroke:none
    style bufferIface fill:none,stroke:none
```

## Notes

- **Bar vs. candle.**
  - **Bar:** A `Bar` is the raw minute bar exactly as Alpaca sends it
    (`t, o, h, l, c, v, n, vw`). Raw bars are cached under `bars:{symbol}`
    by `MarketIngestion`; see [marketingestion.md](marketingestion.md).
  - **Candle:** A `Candle` is our backend object (symbol, open time, open,
    high, low, close, volume). The only place that turns a bar into a candle
    is the static factory method `Candle.from(symbol, bar)`; `LiveCandleStream`
    and `CandleBackfiller` both call it. The symbol is passed separately
    because a raw bar does not carry it.

  Both are saved in Redis under different keys, so the same minute can exist
  twice in two shapes:
  - `bars:{symbol}`: raw bars from `/v2/stocks/bars` responses, owned by
    `MarketIngestion`. Live bars from the WebSocket are not saved raw;
  - `candles:{symbol}`: candles, owned by `CandleBuffer`, last 60 per symbol.
- **`CandleBuffer` decides what data it needs.** `CandleGapDetector` is the
  only component that decides which candles are missing. It compares the
  stored candles with the trading minutes (from `getMarketStatus()`) and
  hands every missing range to `CandleBackfiller`. It runs:
  - at startup, for every stock from `getTrackedStocks()`;
  - on each live candle from `LiveCandleStream` (a new candle more than one
    trading minute after the last stored one);
  - before each `getLastCandles(symbol, n)` read (fewer than n candles, or a
    missing minute between them). The read waits for the backfill and then
    returns the full n, so `getLastCandles(symbol, 60)` always returns 60
    candles.

  "Last 60" means the last 60 trading minutes, not the last 60 clock minutes:
  when the market is closed the check looks at the end of the previous
  session, and nights and weekends do not count as gaps.

  `CandleBackfiller` has no detection logic. For each missing range it
  builds the `BarsRequest` itself: symbol, `timeframe=1Min` and only that
  range (`start`, `end`, `limit`, `sort`). `AlpacaRestBarsClient` adds `feed=iex`
  (the only feed with recent data on the free plan). The backfiller then
  converts the bars with `Candle.from` and saves them. `CandleRedisRepository`
  only stores and reads candles. Candles are keyed by timestamp, so refetched
  candles overwrite existing ones instead of duplicating them.
- **This iteration implements only the happy path.** We assume the Alpaca
  WebSocket never fails, so no gaps appear in the live data. Until that
  changes:
  - `CandleGapDetector` is not built;
  - `LiveCandleStream` saves each candle directly to Redis;
  - `CandleBackfiller` runs only at server startup, to fill the first 60
    candles per symbol;
  - `CandleHistory` only reads the last candles from Redis.

  The diagram still shows `CandleGapDetector`, on its own, so it is ready if we add it later.
- **Errors from `getBars`.** `CandleBackfiller` handles
  `AlpacaConfigException` and `AlpacaUnavailableException`. The status
  codes, the single 429 retry and the timeouts are in
  [marketingestion.md](marketingestion.md).
