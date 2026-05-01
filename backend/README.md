# Cooking Backend API

This repository contains the Spring Boot backend source code for the "Cooking" application, focusing heavily on a robust Authentication and User Management foundational layer.

## Features Added

*   **Security & Auth**:
    *   JWT-based Authentication.
    *   Secure password hashing with `BCryptPasswordEncoder`.
    *   Role-Based Access Control (`CLIENT`, `ADMIN`, `SUPERADMIN`).
    *   Soft-delete implemented for the `User` entity.
    *   Rate limiting utilizing `Bucket4j` protecting sensitive endpoints (e.g., login, register, forgot-password).
*   **API Ecosystem**:
    *   OpenAPI / Swagger configuration with Bearer JWT inclusion for immediate endpoint testing (`http://localhost:8080/swagger-ui.html`).
    *   Dedicated MapStruct configuration for optimized DTO processing.
*   **Mail Capability**:
    *   `JavaMailSender` support to blast OTP verification emails upon signup and password resets.
*   **Validation & Error Responses**:
    *   Standardized exceptions and a Global Exception Handler yielding structured JSON errors (`status`, `description`, `timestamp`).

## Prerequisites
*   Java 17+
*   PostgreSQL
*   Maven 3.x+

## Setup & Execution

### 1. Database configuration
Make sure to provision a local or remote PostgreSQL database. Default expectation: `localhost:5432/cooking_db`.
You can enforce table creation either manually via `database/schema.sql` or automatically relying on application property `spring.jpa.hibernate.ddl-auto=update`.

### 2. Environment Variables
Copy `.env.example` into a local `.env` file (or expose these env variables in your system):
```bash
cp .env.example .env
```
Ensure you provide real `DB_USERNAME`, `DB_PASSWORD`, a secure `JWT_SECRET`, and correct `SMTP` fields for the mail system to work effectively.

### 3. Build & Run
From the root directory of the backend interface, launch standard Maven command:
```bash
./mvnw clean install
./mvnw spring-boot:run
```
