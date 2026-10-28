# OMAN Finance backend module diagram

## 1. Module view

```mermaid
flowchart LR
    alpaca["Alpaca<br/>Market Data WS + REST"]
    browser["Browser<br/>React app"]
    pg[("PostgreSQL<br/>Stock")]
    redis[("Redis<br/>bars:{symbol}")]

    subgraph backend["Backend (Spring Boot)"]
        ingestion["MarketIngestion<br/><i>incl. MarketClock</i>"]
        history["BarBuffer"]
        gateway["ClientFeed"]
    end

    alpaca -->|"WS: minute bars"| ingestion
    alpaca -->|"REST: /v2/clock<br/>/v2/stocks/bars"| ingestion
    ingestion -->|"reads symbols"| pg

    ingestion -->|"onNewBar(bar)"| history
    ingestion -->|"onNewBar(bar)"| gateway
    gateway -->|"getLastBars(symbol, 60)"| history
    history -->|"getTrackedSymbols()<br/>fetchLastBars(symbol, 60)"| ingestion
    gateway -->|"getTrackedSymbols()<br/>getMarketStatus()"| ingestion

    history -->|"ZADD + trim / ZRANGE"| redis
    gateway <-->|"REST /api/*<br/>STOMP /ws"| browser
```

## 2. Component view

Arrows in this view show the direction data flows. The red path is a live
bar travelling from Alpaca to the browser. Dashed hexagons are interfaces.

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
  "flowchart": { "curve": "basis", "nodeSpacing": 45, "rankSpacing": 70, "padding": 18, "htmlLabels": true }
}}%%
flowchart TB
    pg[("<b>PostgreSQL</b><br/><small>Stock</small>")]
    alpaca["<b>Alpaca</b>"]

    subgraph ingestion["<b>marketingestion</b>"]
        direction LR
        stockRepo["<b>StockRepository</b><br/><small>SELECT symbol FROM Stock</small>"]
        streamClient["<b>AlpacaStreamClient</b><br/><small>WS connect, auth, subscribe, reconnect</small>"]
        dispatcher["<b>BarDispatcher</b><br/><small>fans out to all BarListeners</small>"]
        barsClient["<b>AlpacaBarsClient</b><br/><small>GET /v2/stocks/bars<br/>historical minute bars</small>"]
        clock["<b>MarketClock</b><br/><small>polls /v2/clock at next open / close<br/>holds is open, next open, next close<br/>in memory</small>"]
        ingestionApi{{"<i>«interface»</i><br/><b>MarketIngestion</b><br/><small>impl: MarketIngestionService</small><br/><small>getTrackedSymbols()<br/>getMarketStatus()<br/>fetchLastBars(symbol, n)</small>"}}
    end

    barListener{{"<i>«interface»</i><br/><b>BarListener</b><br/><small>onNewBar(Bar)</small>"}}

    subgraph history["<b>barbuffer</b>"]
        direction LR
        recorder["<b>BarRecorder</b><br/><small>implements BarListener<br/>detects gaps</small>"]
        backfiller["<b>BarBackfiller</b><br/><small>on startup and on gap:<br/>refill to 60 bars</small>"]
        barRepo["<b>BarRedisRepository</b><br/><small>ZADD, ZREMRANGEBYRANK, ZRANGE</small>"]
        historyApi{{"<i>«interface»</i><br/><b>BarBuffer</b><br/><small>getLastBars(symbol, n)</small>"}}
    end

    subgraph gateway["<b>clientfeed</b>"]
        direction LR
        broadcaster["<b>BarBroadcaster</b><br/><small>implements BarListener</small>"]
        wsConfig["<b>WebSocketConfig</b><br/><small>STOMP endpoint /ws</small>"]
        rest["<b>REST controllers</b><br/><small>GET /api/stocks<br/>GET /api/bars/{symbol}<br/>GET /api/market/status</small>"]
    end

    browser["<b>Browser</b>"]

    redis[("<b>Redis</b><br/><small>bars:{symbol}</small>")]

    alpaca -->|"WS bars"| streamClient
    alpaca -->|"REST /v2/clock"| clock
    alpaca -->|"REST /v2/stocks/bars"| barsClient
    pg --> stockRepo

    streamClient --> dispatcher
    stockRepo -->|"symbols, loaded once"| ingestionApi
    clock -->|"status"| ingestionApi
    barsClient -->|"historical bars"| ingestionApi
    ingestionApi -->|"getTrackedSymbols()"| streamClient

    dispatcher --> barListener
    barListener -.->|"implemented by"| recorder
    barListener -.->|"implemented by"| broadcaster

    recorder -->|"save live bar"| barRepo
    recorder -->|"gap detected"| backfiller
    ingestionApi -->|"getTrackedSymbols()<br/>fetchLastBars(symbol, 60)"| backfiller
    backfiller -->|"save fetched bars"| barRepo
    barRepo -->|"last n bars"| historyApi

    broadcaster -->|"/topic/bars/{symbol}"| wsConfig
    ingestionApi -->|"getMarketStatus()<br/>getTrackedSymbols()"| rest
    historyApi -->|"getLastBars(symbol, 60)"| rest

    wsConfig -->|"STOMP /ws"| browser
    rest <-->|"REST /api/*"| browser

    historyApi ~~~ redis
    barRepo -->|"save: ZADD + trim to 60"| redis
    redis -->|"read: ZRANGE"| barRepo

    classDef external   fill:#161616,stroke:#d4d4d4,stroke-width:2px,color:#f5f5f5
    classDef datastore  fill:#3f3f3f,stroke:#d4d4d4,stroke-width:1.5px,color:#f5f5f5
    classDef component  fill:#262626,stroke:#737373,stroke-width:1px,color:#e5e5e5
    classDef iface      fill:#262626,stroke:#d4d4d4,stroke-width:1.5px,stroke-dasharray:5 3,color:#f5f5f5

    class alpaca,browser external
    class pg,redis datastore
    class stockRepo,streamClient,dispatcher,barsClient,clock,recorder,backfiller,barRepo,rest,broadcaster,wsConfig component
    class ingestionApi,barListener,historyApi iface

    style ingestion fill:#161616,stroke:#525252,stroke-width:1px,color:#e5e5e5
    style history   fill:#161616,stroke:#525252,stroke-width:1px,color:#e5e5e5
    style gateway   fill:#161616,stroke:#525252,stroke-width:1px,color:#e5e5e5

    linkStyle 0,4,9,10,11,17,20 stroke:#ef4444,stroke-width:2px
```

## 3. Reading the diagrams

- Hexagons are the public contracts between modules. All other components are
  package-private to their module.
- `BarListener` is a shared contract in a common package. `MarketIngestion`
  depends only on the interface; Spring injects every `BarListener` bean into
  `BarDispatcher`.
- Dependencies point one way: `ClientFeed` depends on `BarBuffer` and
  `MarketIngestion`; `BarBuffer` depends on `MarketIngestion`;
  `MarketIngestion` depends on no other module.
- `BarBuffer` always holds the last 60 bars per symbol. The live stream
  alone would leave it empty after startup or with holes after a reconnect, so
  `BarBackfiller` fills it from `MarketIngestion.fetchLastBars(symbol, 60)`:
  - at startup, for every symbol from `getTrackedSymbols()`;
  - when `BarRecorder` sees a gap (a new bar more than one minute after the
    previous one while the market is open).
  Bars are keyed by timestamp, so refetched bars overwrite existing ones
  instead of duplicating them.
- `fetchLastBars` calls Alpaca's historical bars endpoint through
  `AlpacaBarsClient`. When the market is closed it returns the last bars of the
  previous session.
- `MarketIngestionService` loads the symbols from PostgreSQL once at startup.
  `AlpacaStreamClient` and `ClientFeed` both get them through
  `getTrackedSymbols()`, so `StockRepository` has a single caller.
- `MarketClock` keeps the market status (`is_open`, `next_open`,
  `next_close`) in memory, not in Redis. It calls Alpaca `/v2/clock` at
  startup, again at the next open or close, and on a periodic safety-net
  refresh. `getMarketStatus()` is delegated to it.
- Redis holds only `bars:{symbol}`, owned by `BarBuffer`.
- Market status is currently pull-only through REST. Pushing status changes on
  `/topic/market-status` is a possible extension.
- REST endpoint names are provisional.
