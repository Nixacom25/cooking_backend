# 🍳 Cooked Backend API

Backend REST API pour application de gestion de recettes.

##  Technologies
- Java 17
- Spring Boot
- Spring Security (JWT)
- Spring Data JPA
- MySQL
- Swagger

##  Authentification
JWT Bearer Token

##  Endpoints principaux

### Auth
POST /api/auth/register  
POST /api/auth/login  

### Categories
GET /api/categories  
POST /api/categories (ADMIN)

### Recipes
GET /api/recipes  
POST /api/recipes  

##  Swagger
http://localhost:8099/swagger-ui.html

## Variables d’environnement

SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/cooked_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
JWT_SECRET=my_super_secret_key_for_cooking_backend_application_2026_should_be_long_enough
JWT_EXPIRATION=86400000
