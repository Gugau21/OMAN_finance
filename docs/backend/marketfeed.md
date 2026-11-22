# MarketFeed

The backend's interface to the browser. It serves snapshots over REST and
pushes live updates over WebSocket.

Part of the [backend module overview](modular-architecture.md).

## Component diagram

Solid arrows show the direction data flows. The red path is a live update,
from `streamCandles()`, `streamStockStats()` and `streamPricePoints()`
through `CandleBroadcaster` and the STOMP endpoint to the browser.

Hexagons are the public interfaces of the modules this one depends on.
Those modules appear only as their interface. The browser is the only
external system this module connects to.

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
    subgraph deps[" "]
        direction LR
        ingestionApi{{"<i>«interface»</i><br/><b>MarketIngestion</b><br/><small>streamBars(): stream of Bars<br/>getBars(BarsRequest): BarsResponse<br/>getMarketStatus(): Clock<br/>streamMarketStatus(): stream of Clocks<br/>getTrackedStocks(): list of Stocks<br/>addSymbol(symbol)<br/>removeSymbol(symbol)</small>"}}
        bufferApi{{"<i>«interface»</i><br/><b>CandleBuffer</b><br/><small>streamCandles(): stream of Candles<br/>getLastCandles(symbol, n)</small>"}}
        metricsApi{{"<i>«interface»</i><br/><b>MarketMetrics</b><br/><small>streamStockStats(): stream of StockStats<br/>getStockStats(symbol): StockStat<br/>streamPricePoints(): stream of PricePoints<br/>getPricePoints(symbol): list of PricePoints</small>"}}
    end

    subgraph feed["<b>marketfeed</b>"]
        direction LR
        rest["<b>REST controllers</b><br/><small>serve snapshots under /api/*</small>"]
        subgraph feedLive[" "]
            direction TB
            broadcaster["<b>CandleBroadcaster</b><br/><small>consumes streamCandles()</small>"]
            wsConfig["<b>WebSocketConfig</b><br/><small>STOMP endpoint /ws</small>"]
        end
    end

    browser["<b>Browser</b>"]

    %% Edge order is the linkStyle index.
    %% 0
    ingestionApi -->|"getMarketStatus()<br/>getTrackedStocks()"| rest
    %% 1 live
    bufferApi -->|"streamCandles()"| broadcaster
    %% 2
    bufferApi -->|"getLastCandles(symbol, 60)"| rest
    %% 3 live
    metricsApi -->|"streamStockStats()"| broadcaster
    %% 4 live
    metricsApi -->|"streamPricePoints()"| broadcaster
    %% 5
    metricsApi -->|"getStockStats(symbol)<br/>getPricePoints(symbol)"| rest
    %% 6 live
    broadcaster -->|"/topic/candles/{symbol}"| wsConfig
    %% 7 live
    wsConfig -->|"STOMP /ws"| browser
    %% 8
    rest <-->|"REST /api/*"| browser

    linkStyle 1,3,4,6,7 stroke:#ef4444,stroke-width:2px

    classDef external   fill:#161616,stroke:#d4d4d4,stroke-width:2px,color:#f5f5f5
    classDef component  fill:#262626,stroke:#737373,stroke-width:1px,color:#e5e5e5
    classDef iface      fill:#262626,stroke:#d4d4d4,stroke-width:1.5px,stroke-dasharray:5 3,color:#f5f5f5

    class browser external
    class rest,broadcaster,wsConfig component
    class ingestionApi,bufferApi,metricsApi iface

    style deps fill:none,stroke:none
    style feed fill:#161616,stroke:#525252,stroke-width:1px,color:#e5e5e5
    style feedLive fill:none,stroke:none
```

## Notes

- MarketFeed never sees Alpaca's HTTP client exceptions.
  `CandleBackfiller` and `MarketStatusTracker` handle
  `AlpacaConfigException` and `AlpacaUnavailableException`. The status
  codes, the single 429 retry and the timeouts are in
  [marketingestion.md](marketingestion.md).
