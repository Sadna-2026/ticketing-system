# Ticketing and Event Management System

A Domain-Driven Design (DDD) based ticketing and event management platform built with Java 21 and Maven.

## Application Structure

The application strictly adheres to Clean Architecture and DDD principles:

- **Domain Layer (`domain.*`)**: The heart of the software. Contains all domain models (Aggregates, Entities, Value Objects), domain services, and repository interfaces. Segregated into distinct sub-domains.
- **Application Layer (`application`)**: Contains use-cases and application services that orchestrate the interactions between domain objects to fulfill business requirements.
- **Infrastructure Layer (`infrastructure`)**: Concrete implementations of interfaces defined in the domain layer (e.g., database repositories, third-party integrations, message brokers).
- **External/Presentation Layer (`external`)**: The entry points of the application such as REST Controllers, CLI adapters, or GraphQL endpoints.

## Getting Started

Ensure you have **Java 21** and **Maven** installed locally.

Build the project and run all tests:
\`\`\`bash
mvn clean verify
\`\`\`
