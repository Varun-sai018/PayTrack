# PayTrack - Digital Wallet System

A microservices-based digital wallet application with API Gateway, authentication, wallet management, transactions, and expense tracking.

## Architecture

- **Gateway**: Spring Cloud Gateway for routing and API management
- **Auth Service**: JWT-based authentication with HS256
- **Wallet Service**: Wallet creation, balance management
- **Transaction Service**: Transaction processing
- **Expense Service**: Expense categorization and tracking
- **Eureka Server**: Service discovery and registration

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.5.5, Spring Cloud 2025.0.0
- **Database**: MySQL 8 (per-service schemas)
- **Cache**: Redis
- **Message Queue**: RabbitMQ
- **Communication**:
  - Synchronous: OpenFeign (REST)
  - Asynchronous: RabbitMQ events
- **Documentation**: SpringDoc OpenAPI / Swagger
- **Mapping**: MapStruct

## Project Structure

```
PayTrack/
├── backend/              # Spring Boot microservices
│   ├── eureka-service/          # Service discovery server (port 8761)
│   ├── api-gateway-service/     # API Gateway (port 8080)
│   ├── auth-service/            # Authentication Service (port 8081)
│   ├── wallet-service/          # Wallet Service (port 8082)
│   ├── transaction-service/     # Transaction Service (port 8083)
│   └── expense-service/         # Expense Service (port 8084)
├── docker-compose.yml    # Infrastructure orchestration
├── pom.xml               # Root Maven parent
├── .gitignore
├── .gitattributes
└── README.md
```

## Communication Patterns

- **Internal API Calls**: OpenFeign (REST)
- **Event-Driven**: RabbitMQ for async communication

## Getting Started

### Prerequisites

- Java 17
- Maven 3.9.6+ (or use the included `mvnw` wrapper)
- Docker & Docker Compose (for infrastructure)

### 1. Start Infrastructure Services

```bash
cd PayTrack
docker-compose up -d
```

This starts:
- MySQL databases on ports 3307–3310
- Redis on port 6379
- RabbitMQ on ports 5672 and 15672

### 2. Start Microservices

Start services in this order:

```bash
# 1. Eureka Server (first)
cd backend/eureka-service
./mvnw spring-boot:run

# 2. API Gateway
cd backend/api-gateway-service
./mvnw spring-boot:run

# 3. Auth Service
cd backend/auth-service
./mvnw spring-boot:run

# 4. Wallet Service
cd backend/wallet-service
./mvnw spring-boot:run

# 5. Transaction Service
cd backend/transaction-service
./mvnw spring-boot:run

# 6. Expense Service
cd backend/expense-service
./mvnw spring-boot:run
```

### 3. Service Ports

| Service | Port |
|---|---|
| Eureka Server | 8761 |
| API Gateway | 8080 |
| Auth Service | 8081 |
| Wallet Service | 8082 |
| Transaction Service | 8083 |
| Expense Service | 8084 |

### 4. Build All Services

```bash
cd PayTrack/backend
./mvnw clean install
```

Or from root:
```bash
cd PayTrack
./mvnw -f backend/pom.xml clean install
```

## Configuration

Each service has its own `src/main/resources/application.yml` with:
- Server port
- Database connection (JPA/Hibernate)
- Redis configuration
- RabbitMQ configuration
- Eureka client registration
- JWT settings
- Logging levels

Environment variables can override defaults (e.g., `SPRING_DATA_REDIS_HOST`, `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`).

## License

MIT