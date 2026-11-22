# MarketMetrics

Derives market-facing statistics from candles. It subscribes to
CandleBuffer's candle stream and computes the values the frontend needs for
stock overviews and sparkline graphs.

Part of the [backend module overview](modular-architecture.md).

## Component diagram

Solid arrows show the direction data flows. Dashed arrows labeled "calls"
point from the caller to the component it calls. The red path is a live
candle, from `streamCandles()` through the live streams to the
`MarketMetrics` interface.

Hexagons are public interfaces. `CandleBuffer` appears only as its
interface. Other nodes in the module box are package-private, except the
`StockStat` and `PricePoint` records, which the interface uses. This module
connects to no external system; it holds no persistent state of its own.

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
    bufferApi{{"<i>«interface»</i><br/><b>CandleBuffer</b><br/><small>streamCandles(): stream of Candles<br/>getLastCandles(symbol, n)</small>"}}

    subgraph metrics["<b>marketmetrics</b>"]
        direction TB
        subgraph metricsStream[" "]
            direction LR
            liveStatStream["<b>LiveStockStatStream</b><br/><small>consumes streamCandles()<br/>calls StockStatCalculator per candle<br/>emits stream of StockStats</small>"]
            priceStream["<b>PricePointStream</b><br/><small>consumes streamCandles()<br/>calls PricePointCalculator per candle<br/>emits stream of PricePoints</small>"]
        end
        subgraph metricsCalc[" "]
            direction LR
            statCalc["<b>StockStatCalculator</b><br/><small>reads the last 60 candles<br/>computes price, change, change %, volume<br/>returns StockStat</small>"]
            priceCalc["<b>PricePointCalculator</b><br/><small>reads the last 60 candles<br/>extracts one close price per candle<br/>returns list of PricePoints</small>"]
        end
        subgraph metricsIface[" "]
            direction LR
            metricsApi{{"<i>«interface»</i><br/><b>MarketMetrics</b><br/><small>streamStockStats(): stream of StockStats<br/>getStockStats(symbol): StockStat<br/>streamPricePoints(): stream of PricePoints<br/>getPricePoints(symbol): list of PricePoints</small>"}}
            statRecord["<i>«record»</i><br/><b>StockStat</b><br/><small>symbol, price, change, changePct, volume</small>"]
            priceRecord["<i>«record»</i><br/><b>PricePoint</b><br/><small>symbol, openTime, price</small>"]
        end
    end

    %% Edge order is the linkStyle index.
    %% 0 live
    bufferApi -->|"streamCandles()"| liveStatStream
    %% 1 live
    bufferApi -->|"streamCandles()"| priceStream
    %% 2
    bufferApi -->|"getLastCandles(symbol, 60)"| statCalc
    %% 3
    bufferApi -->|"getLastCandles(symbol, 60)"| priceCalc
    %% 4
    liveStatStream -.->|"calls"| statCalc
    %% 5
    priceStream -.->|"calls"| priceCalc
    %% 6 live
    metricsApi <-->|"stream of StockStats"| liveStatStream
    %% 7 live
    metricsApi <-->|"stream of PricePoints"| priceStream
    %% 8
    metricsApi <-->|"getStockStats(symbol)"| statCalc
    %% 9
    metricsApi <-->|"getPricePoints(symbol)"| priceCalc

    linkStyle 0,1 stroke:#ef4444,stroke-width:2px
    linkStyle 6,7 stroke:#ef4444,stroke-width:2px,marker-end:none
    linkStyle 8,9 marker-end:none

    classDef component  fill:#262626,stroke:#737373,stroke-width:1px,color:#e5e5e5
    classDef iface      fill:#262626,stroke:#d4d4d4,stroke-width:1.5px,stroke-dasharray:5 3,color:#f5f5f5

    class statCalc,priceCalc,liveStatStream,priceStream,statRecord,priceRecord component
    class bufferApi,metricsApi iface

    style metrics fill:#161616,stroke:#525252,stroke-width:1px,color:#e5e5e5
    style metricsStream fill:none,stroke:none
    style metricsCalc fill:none,stroke:none
    style metricsIface fill:none,stroke:none
```

## Notes

The component write-up has no separate notes for this module. The
calculators read the last 60 candles through `CandleBuffer` and return
`StockStat` and `PricePoint`; nothing here is stored.
