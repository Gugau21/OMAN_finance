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
- `GET /api/v1/ping` - API smoke test

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
