# Spécifications et Documentation Technique - Cooked Backend

Ce dossier contient les services serveurs du projet **Cooked**. 

## Structure du Dossier
* [backend](file:///home/ousseynou_diedhiou/Bureau/Nixacom/cooked/cooked%20backend/backend) : Code source principal de l'API REST Spring Boot.
* [docker-compose.yml](file:///home/ousseynou_diedhiou/Bureau/Nixacom/cooked/cooked%20backend/docker-compose.yml) : Script de conteneurisation pour déployer PostgreSQL et Redis en local lors des phases de développement et test.

---

## 1. Description Détaillée de l'API REST Spring Boot
La documentation complète de l'architecture de l'API, de la base de données PostgreSQL, de la configuration du cache Redis, du rate limiting et des intégrations avec OpenAI et Apple Store IAP est disponible dans le fichier de documentation dédié :
👉 **[Documentation Technique Backend (Spring Boot)](file:///home/ousseynou_diedhiou/Bureau/Nixacom/cooked/cooked%20backend/backend/TECHNICAL_DOCUMENTATION.md)**

---

## 2. Docker Compose
Le fichier `docker-compose.yml` permet d'exécuter localement la base de données et le cache dans des conteneurs isolés :

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: cooking_postgres
    environment:
      POSTGRES_DB: cooking_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    container_name: cooking_redis
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

### Pour lancer l'environnement local :
1. Assurez-vous que Docker et Docker Compose sont installés.
2. Exécutez la commande suivante à la racine de ce dossier :
   ```bash
   docker-compose up -d
   ```
3. L'API Spring Boot pourra alors se connecter à PostgreSQL sur le port `5432` et à Redis sur le port `6379`.
