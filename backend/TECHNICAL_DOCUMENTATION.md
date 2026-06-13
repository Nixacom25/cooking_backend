# Document de Spécifications et Documentation Technique - Cooked Backend

Ce document présente l'architecture complète, les services, les choix techniques et l'état des réalisations du backend de l'application **Cooked** (Spring Boot & PostgreSQL & Redis).

---

## 1. Vue d'ensemble de l'Architecture

Le backend de Cooked est conçu sous la forme d'une API REST **stateless** développée avec **Spring Boot 3.x**. 

```mermaid
graph TD
    Client[Application Flutter / Mobile] -->|HTTPS| API[Spring Boot REST API]
    API -->|Secured by JWT| AuthFilter[JwtAuthenticationFilter]
    API -->|Read/Write| DB[(PostgreSQL Database)]
    API -->|Caching & Session Blacklist| Redis[(Redis Cache)]
    API -->|Scraping / OCR / Generation| OpenAI[OpenAI API (GPT-4o & GPT-4o-mini)]
    API -->|Verification| AppleAPI[Apple App Store API]
    API -->|Image Uploads| Cloudinary[Cloudinary Service]
    API -->|Asynchronous Background Tasks| Async[Task Executor]
```

### Stack Technique :
* **Language/Framework** : Java 17 / Spring Boot 3.2+
* **Persistance** : Spring Data JPA / Hibernate
* **Base de données** : PostgreSQL 15+
* **Cache & Session** : Redis (Spring Cache & Blacklist de tokens)
* **Sécurité** : Spring Security 6 / JWT (JSON Web Tokens)
* **Documentation API** : OpenAPI 3 / Swagger UI

---

## 2. Base de Données & Modèle de Données

Le schéma de base de données est structuré pour supporter une forte volumétrie de lectures (les recettes) et un accès concurrentiel efficace.

### Schéma Conceptuel (Entités Clés) :
* **User** : Gère l'identité, les préférences alimentaires, les allergies, le budget et le statut de l'abonnement.
* **Recipe** : Représente une recette (nom, temps de préparation, kcal, origine `SUGGESTED` ou `SCRAPED`, etc.). Elle possède un index sur `user_id` et `is_public`.
* **Ingredient** & **RecipeIngredient** : Table de liaison pour les ingrédients avec leurs quantités respectives.
* **UserSubscription** & **SubscriptionPayment** : Gèrent les informations d'abonnement In-App (Apple/Google) et l'historique des paiements.
* **DeviceSession** : Historique et tracking des appareils connectés.
* **BlacklistedToken** : Utilisé pour invalider les sessions JWT lors de la déconnexion.

### Indexation de Production (Optimisation) :
Afin de garantir des temps de réponse inférieurs à 100 ms avec des milliers de recettes et d'utilisateurs, des index B-Tree explicites ont été configurés :
* `@Index(name = "idx_user_email", columnList = "email")`
* `@Index(name = "idx_recipe_user", columnList = "user_id")`
* `@Index(name = "idx_recipe_is_public", columnList = "is_public")`
* `@Index(name = "idx_recipe_category", columnList = "category_id")`

---

## 3. Services & Intégrations Externes

### A. OpenAI Service (`AiServiceImpl`)
Le moteur IA de Cooked utilise les modèles de langage d'OpenAI configurés en mode JSON :
* **Vision (GPT-4o-mini)** : Détection automatique des ingrédients à partir d'une photo/capture d'écran importée.
* **Scraping & Extraction (GPT-4o)** : Extraction structurée de recettes à partir d'un lien web (YouTube, blog de cuisine, etc.) en nettoyant le texte via `Jsoup`.
* **Génération de Recettes (GPT-4o-mini)** : Génération personnalisée de recettes en combinant les ingrédients disponibles, les allergies et les préférences de l'utilisateur.

### B. In-App Purchase Verification (`SubscriptionServiceImpl`)
Service critique gérant la vérification des reçus d'achat In-App Store d'Apple :
* **Normalisation Base64** : Correction automatique des payload corrompus (gestion automatique du padding `=` et remplacement des espaces par des `+` suite aux encodages URL).
* **Robustesse Sandbox** : Redirection automatique vers l'URL sandbox d'Apple en cas d'erreur de reçu de test (code 21007) et mécanisme de bypass sécurisé pour les tests TestFlight.

### C. Gestion Asynchrone (`UserInitializationServiceImpl`)
* L'initialisation d'un compte (génération automatique des premières recettes personnalisées à l'inscription) est marquée comme `@Async`.
* Elle s'exécute dans un thread séparé en tâche de fond **après le commit de la transaction** (`TransactionSynchronizationManager.registerSynchronization`), évitant les blocages d'accès concurrentiel en écriture.

### D. Envoi d'Emails (`EmailServiceImpl`)
* Intégration de `JavaMailSender` pour envoyer de façon asynchrone les OTP de vérification de compte, de réinitialisation de mot de passe et d'alertes de mise à jour.

---

## 4. Configurations Techniques Clés

### Connexions Base de Données (`application.properties`) :
```properties
# Hikari Connection Pool (Optimisé pour Render / PostgreSQL)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.idle-timeout=10000
spring.datasource.hikari.max-lifetime=240000
```

### Redis & Caching :
* Activé via `spring.cache.type=redis`.
* Caching des requêtes d'exploration et des recettes populaires pour réduire les lectures en base de données.
* Utilisation d'un wrapper Jackson personnalisé (`RestPageImpl`) pour désérialiser correctement les listes paginées Spring Data issues du cache Redis.

### Sécurité & CORS :
* Spring Security configure l'API comme totalement **Stateless** (pas de session HTTP locale).
* Filtre CORS ouvert pour accepter les requêtes de l'application mobile et du dashboard web.
* Swagger/OpenAPI désactivé par défaut en production pour des raisons de sécurité.

### Compression Réseau :
```properties
server.compression.enabled=true
server.compression.mime-types=application/json,application/javascript,text/css
server.compression.min-response-size=1024
```
Réduit la taille des réponses réseau de plus de 70%, très important pour les utilisateurs mobiles disposant d'une faible couverture réseau.

---

## 5. Résumé des Réalisations Importantes (Historique & Correctifs)

1. **Intégration Apple IAP** : Fiabilisation du processus de vérification de reçu, correction de l'erreur `21002` et sécurisation des logs de paiement.
2. **Désérialisation Redis** : Correction du bug de désérialisation de `PageImpl` en introduisant le constructeur Jackson annoté pour les pages de recettes paginées.
3. **Optimisation du Démarrage** : Désactivation des seeders de base de données automatiques en production (`ExploreDataSeederListener`) pour accélérer le déploiement et éviter les collisions de données.
4. **Rate Limiting** : Ajout de limites de requêtes sur les routes sensibles d'authentification et d'OTP.
5. **Logs de Production** : Configuration fine des traces de débogage pour repérer rapidement les erreurs sans surcharger les disques.

---

## 6. Variables d'Environnement Requises en Production

| Variable | Description | Exemple / Défaut |
| :--- | :--- | :--- |
| `PORT` | Port d'écoute du serveur | `8099` |
| `DB_URL` | URL de la base PostgreSQL | `jdbc:postgresql://host:port/dbname` |
| `DB_USERNAME` | Utilisateur PostgreSQL | `postgres` |
| `DB_PASSWORD` | Mot de passe PostgreSQL | `***` |
| `JWT_SECRET` | Clé secrète de signature des jetons | *(chaîne aléatoire longue de 256 bits)* |
| `REDIS_HOST` | Hôte Redis | `localhost` |
| `REDIS_PORT` | Port Redis | `6379` |
| `OPENAI_API_KEY` | Clé secrète OpenAI | `sk-proj-...` |
| `CLOUDINARY_URL` | URL de connexion Cloudinary | `cloudinary://key:secret@cloudname` |
| `SMTP_USERNAME` | Nom d'utilisateur SMTP (email) | `contact@cookedapp.com` |
| `SMTP_PASSWORD` | Mot de passe d'application SMTP | `***` |
