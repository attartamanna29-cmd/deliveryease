# DeliverEase - Delivery Management System

Java 17 - Spring Boot 3 - Spring Data JPA / Hibernate - MySQL - Spring Security + JWT - HTML/CSS/JavaScript - Maven

## Run
1. Install JDK 17+, Maven and MySQL 8 (MySQL Workbench optional).
2. Edit `src/main/resources/application.properties` and set your MySQL `username` / `password`.
   The `deliverease` database is created automatically.
3. From this folder run:  `mvn spring-boot:run`   (or open the folder in VS Code and run `DeliverEaseApplication`)
4. Open http://localhost:8080

## Demo accounts (created on first start)
| Role | Email | Password |
|---|---|---|
| Admin | admin@deliverease.com | admin123 |
| Delivery Partner | ravi@deliverease.com / anita@deliverease.com | partner123 |
| Customer | register from the login page | - |

## REST API summary
| Method | Endpoint | Access |
|---|---|---|
| POST | /api/auth/register, /api/auth/login | Public |
| GET/PUT | /api/profile | Any logged-in user |
| GET | /api/track/{trackingId} | Public |
| POST | /api/orders | Customer |
| GET | /api/orders[?status=] | Admin (all) / Customer (own) / Partner (assigned) |
| PUT | /api/orders/{id}/assign?partnerId= | Admin |
| PUT | /api/orders/{id}/cancel | Customer (own) / Admin |
| PUT | /api/orders/{id}/status | Delivery Partner |
| GET/POST/PUT | /api/partners, /api/partners/{id}, /api/partners/{id}/active?value= | Admin |
| GET | /api/dashboard | Admin |
