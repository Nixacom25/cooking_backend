# Vérification Backend - Gestion des Abonnements

## État Actuel du Backend

### ✅ Ce qui est implémenté

#### 1. Webhook RevenueCat (RevenueCatWebhookController.java)
- **Endpoint**: `/subscriptions/revenuecat-webhook` et `/webhooks/revenuecat`
- **Sécurité**: Vérification du secret webhook via header Authorization
- **Événements gérés**:
  - ✅ `INITIAL_PURCHASE` → Met subscriptionStatus à ACTIVE
  - ✅ `RENEWAL` → Met subscriptionStatus à ACTIVE
  - ✅ `PRODUCT_CHANGE` → Met subscriptionStatus à ACTIVE
  - ✅ `UNCANCELLATION` → Met subscriptionStatus à ACTIVE
  - ✅ `NON_RENEWING_PURCHASE` → Met subscriptionStatus à ACTIVE
  - ✅ `EXPIRATION` → Met subscriptionStatus à EXPIRED
  - ✅ `CANCELLATION` → Met subscriptionStatus à EXPIRED
  - ✅ `BILLING_ISSUE` → Envoie email de notification d'échec de paiement

- **Détection du plan**:
  - ✅ Détecte yearly vs monthly basé sur productId (contient "year")
  - ✅ Met à jour subscriptionType (YEARLY ou MONTHLY)
  - ✅ Met à jour subscriptionExpiresAt

- **Recherche utilisateur**:
  - ✅ Recherche par email
  - ✅ Recherche par UUID si email non trouvé
  - ✅ Sauvegarde les modifications dans la base de données

#### 2. États d'abonnement (SubscriptionStatus enum)
```java
public enum SubscriptionStatus {
    FREE,
    TRIAL,
    ACTIVE,
    EXPIRED,
    CANCELLED,
    PREMIUM
}
```
- ✅ Les états nécessaires sont définis

#### 3. Configuration de sécurité (SecurityConfig.java)
- ✅ Webhook RevenueCat accessible publiquement (permitAll)
- ✅ Autres endpoints nécessitent authentication JWT

### ❌ Ce qui manque ou doit être amélioré

#### 1. Gestion de l'état TRIAL
**Problème**: Le webhook met tout à ACTIVE, même pour les essais gratuits.

**Solution requise**:
```java
// Dans RevenueCatWebhookController.java
if ("INITIAL_PURCHASE".equalsIgnoreCase(eventType)) {
    // Vérifier si c'est un essai gratuit
    Boolean isTrial = (Boolean) event.get("is_trial");
    if (isTrial != null && isTrial) {
        user.setSubscriptionStatus(SubscriptionStatus.TRIAL);
    } else {
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
    }
}
```

#### 2. Middleware de vérification d'abonnement
**Problème**: Aucun middleware ne vérifie le statut d'abonnement sur chaque requête API.

**Solution requise**: Créer un filtre/intercepteur qui :
- Vérifie `subscriptionStatus` de l'utilisateur authentifié
- Si `subscriptionStatus` est `FREE` ou `EXPIRED` → Retourne 403 Forbidden
- Si `subscriptionStatus` est `ACTIVE` ou `TRIAL` et `subscriptionExpiresAt` est dans le futur → Autorise
- Sinon → Retourne 403 Forbidden

```java
// Créer SubscriptionFilter.java
@Component
public class SubscriptionFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        // Vérifier subscriptionStatus
        // Si non abonné, retourner 403
    }
}
```

#### 3. Endpoint pour vérifier le statut d'abonnement
**Problème**: L'endpoint `/user/me` retourne l'utilisateur mais ne force pas la vérification d'abonnement.

**Solution requise**: Ajouter une vérification dans `UserService.getCurrentUser()`:
```java
public UserResponse getCurrentUser(String email) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    // Vérifier statut d'abonnement
    if (user.getSubscriptionStatus() == SubscriptionStatus.FREE ||
        user.getSubscriptionStatus() == SubscriptionStatus.EXPIRED) {
        throw new SubscriptionRequiredException("Active subscription required");
    }

    return mapToResponse(user);
}
```

#### 4. Marquage des nouveaux comptes comme FREE
**Problème**: Les nouveaux comptes doivent être marqués comme FREE (ou INACTIVE) lors de la création.

**Solution requise**: Dans `AuthController.register()`:
```java
User user = new User();
user.setSubscriptionStatus(SubscriptionStatus.FREE); // Marquer comme FREE
user.setSubscriptionType(SubscriptionType.NONE);
```

#### 5. Configuration des produits RevenueCat
**Problème**: Le backend ne valide pas que les produits correspondent à la configuration attendue.

**Solution requise**: Ajouter une validation des Product IDs:
- `monthly_sub` doit avoir 0 jours d'essai
- `yearly_sub` doit avoir 3 jours d'essai

## Actions Requises pour l'Équipe Backend

### Priorité 1 (Critique)
1. **Ajouter le middleware de vérification d'abonnement**
   - Bloquer toutes les requêtes API des utilisateurs non abonnés
   - Retourner 403 avec message "Active subscription required"

2. **Marquer les nouveaux comptes comme FREE**
   - Modifier l'endpoint register pour initialiser subscriptionStatus à FREE
   - Empêcher l'accès sans abonnement

### Priorité 2 (Important)
3. **Gérer correctement l'état TRIAL**
   - Détecter les essais gratuits depuis le webhook RevenueCat
   - Mettre subscriptionStatus à TRIAL pour les essais gratuits

4. **Ajouter vérification dans UserService.getCurrentUser()**
   - Lever une exception si utilisateur non abonné
   - Force le client à rediriger vers l'écran d'abonnement

### Priorité 3 (Recommandé)
5. **Logger les tentatives d'accès non autorisées**
   - Surveiller les utilisateurs qui tentent d'accéder sans abonnement
   - Détecter les contournements potentiels

6. **Ajouter des tests unitaires**
   - Tester le middleware de vérification
   - Tester les webhook events
   - Tester la logique de TRIAL vs ACTIVE

## Configuration RevenueCat à Confirmer

L'équipe backend doit confirmer dans le dashboard RevenueCat :
- ✅ Entitlement ID: `premium`
- ✅ Product ID Monthly: `monthly_sub` (0 jours d'essai)
- ✅ Product ID Yearly: `yearly_sub` (3 jours d'essai)
- ✅ Webhook endpoint configuré: `https://api.cookedapp.com/subscriptions/revenuecat-webhook`
- ✅ Webhook secret configuré dans `.env` (`revenuecat.webhook.secret`)

## Conclusion

Le backend a une base solide avec le webhook RevenueCat, mais **il manque la couche de sécurité critique** qui bloque l'accès aux utilisateurs non abonnés. Le middleware de vérification d'abonnement est **essentiel** pour empêcher les contournements.
