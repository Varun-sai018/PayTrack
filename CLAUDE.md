# PayTrack - Digital Wallet System

## Project Overview
A microservices-based digital wallet application with API Gateway, authentication, wallet management, transactions, and expense tracking.

## Architecture
- **Gateway**: Spring Cloud Gateway for routing and API management
- **Auth Service**: JWT-based authentication with HS256
- **Wallet Service**: Wallet creation, balance management
- **Transaction Service**: Transaction processing
- **Expense Service**: Expense categorization and tracking

## Tech Stack
- **Backend**: Java 17, Spring Boot 3.x, Spring Cloud Gateway
- **Database**: MySQL 8 (per-service schemas)
- **Migration**: Flyway
- **Cache**: Redis
- **Message Queue**: RabbitMQ
- **Communication**:
  - Synchronous: OpenFeign (REST)
  - Asynchronous: RabbitMQ events

## Project Structure
```
PayTrack/
├── backend/              # Spring Boot microservices
│   ├── gateway/          # API Gateway service
│   ├── auth/             # Authentication service
│   ├── wallet/           # Wallet management service
│   ├── transaction/      # Transaction service
│   └── expense/          # Expense tracking service
├── frontend/             # React application
└── docker-compose.yml    # Infrastructure orchestration
```

## Communication Patterns
- **Internal API Calls**: OpenFeign (REST)
- **Event-Driven**: RabbitMQ for async communication
