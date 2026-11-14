# OMAN Finance backend

This is a Spring Boot 3 application using Java 21, Gradle Kotlin DSL, PostgreSQL, and Spring Data JPA.

## Run with Docker Compose

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

The backend is available directly at `http://localhost:8080`. When the development or production Nginx profile is enabled, use the Nginx port instead.

Useful endpoints:

- `GET /actuator/health` - application and database health
- `GET /api/market/status` - whether the market is open, and the next open and close times
- `GET /api/stocks` - symbol and company name of every tracked stock

The full REST API, including the error responses, is described in [`docs/api_contract.yaml`](../docs/api_contract.yaml).

## Live updates (STOMP)

Browsers get live updates over one WebSocket at `/ws`: `ws://localhost/ws` through Nginx, or `ws://localhost:8080/ws` directly in development. It speaks STOMP 1.2 without SockJS.

- Heart-beats are `10000,10000`, so Nginx does not close a connection that is quiet while the market is closed.
- Clients only `SUBSCRIBE`. Every `SEND` from a client gets an `ERROR` frame, because the broker would otherwise forward it to all subscribers.
- Allowed origins come from `CORS_ALLOWED_ORIGINS`, the same as for the REST API.

Topics:

- `/topic/market/status` - every market status the backend gets from Alpaca, with the same payload as `GET /api/market/status`

The full list of topics and payloads is in [`docs/stomp_contract.yaml`](../docs/stomp_contract.yaml).

To publish to a new topic from backend code, inject `SimpMessageSendingOperations` and call `convertAndSend("/topic/...", payload)`. The broker is set up in `shared/web/WebSocketConfig`; `marketfeed/web/MarketStatusPublisher` is an example.

## Fake market data

Set `SPRING_PROFILES_ACTIVE=fake` to run without Alpaca keys and without connecting to Alpaca. The market is then always open, the backend tracks 5 stocks with synthetic bars, and one synthetic bar per stock arrives every minute. `/topic/market/status` stays silent, as the status never changes; read it from `GET /api/market/status`.

Use it whenever someone else runs the real backend with the same Alpaca keys: Alpaca allows one stream connection per key, and a second one is refused with error 406.

## Run locally

Start PostgreSQL first, then set the datasource variables from `.env.example` with a host-based URL such as `jdbc:postgresql://localhost:5432/emppulse_db`.

Run the application with:

```powershell
gradle bootRun
```

Run the tests with:

```powershell
gradle test
```

The project currently uses `spring.jpa.hibernate.ddl-auto=validate`; database schema changes should be introduced through versioned migrations before adding entities.
