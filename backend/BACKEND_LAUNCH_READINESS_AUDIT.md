# Cooked Backend — Launch Readiness Audit

**Date:** 2026-09-24
**Scope:** Full static-code audit of `cooked backend/backend` (Spring Boot 3.2.5, Java 17), all 28 controllers / 169 mapped routes, cross-referenced against `mobile/`, `landing_page/`, and `frontend/` to determine real consumers.

**Method:** This is a code-reading audit, not a load test. "Response time" and "5,000 concurrent users" assessments are engineering judgment based on query patterns, external calls, and connection/thread pool configuration — not measured benchmarks. Running an actual load test against the live Render-hosted backend was intentionally *not* done here (real risk of triggering an outage against production infrastructure); see the Recommendations section for how to do that safely if you want real numbers before launch.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Infrastructure & Cross-Cutting Concerns](#infrastructure--cross-cutting-concerns)
3. [Third-Party Dependency Inventory](#third-party-dependency-inventory)
4. [Routes — Auth / User / Session / Subscription](#routes--auth--user--session--subscription)
5. [Routes — Recipes / Content / AI](#routes--recipes--content--ai)
6. [Routes — Meal Plans / Grocery / Cookbooks / Categories](#routes--meal-plans--grocery--cookbooks--categories)
7. [Routes — Admin / Ops / Support / Notifications](#routes--admin--ops--support--notifications)
8. [Routes — Misc (Root, Share, Well-Known)](#routes--misc-root-share-well-known)
9. [External AI Service Integration (Markhor)](#external-ai-service-integration-markhor)
10. [Cross-Cutting Bugs Found During This Audit](#cross-cutting-bugs-found-during-this-audit)
11. [Prioritized Recommendations Before Launch](#prioritized-recommendations-before-launch)

---

## Executive Summary

**Bottom line: not ready for a public launch as-is, but every blocking issue found has a targeted, same-day fix — none require an architecture change.** This audit covered all 28 controllers / 169 mapped routes in the backend by direct code reading (not a live load test — see Method above), cross-referenced against `mobile/`, `landing_page/`, and `frontend/` to confirm real consumers for every route.

**What's genuinely solid:** the core CRUD surface (recipes, cookbooks, grocery items, sessions, saved ingredients) is consistently well-built — ownership checks are present and correct on almost every user-owned resource, pagination is uniformly clamped after this session's earlier hardening pass, the `@Async` executor is explicitly tuned for scale ("Optimized configuration for 50k+ users"), Redis caching is applied sensibly to the highest-traffic public reads, actuator exposure is safe, and error responses don't leak stack traces. This is not a codebase with systemic quality problems — the issues below are specific, fixable gaps, not a pattern of neglect.

**What blocks launch (9 items, tier P0 below):** this audit found a small number of routes where authorization was simply never added, not weakened — most seriously, the entire `RecipeDataController` (`/api/recipe-data/**`) has no authentication at all, and one of its routes can trigger real, billed OpenAI image-generation calls for free; separately, `POST /admin/seed/explore/reset` can permanently wipe the entire public Explore catalog with no role check and no working recovery path. Both read as routes that were built for internal/admin tooling and simply never got their `@PreAuthorize` added — a fast, mechanical fix, not a design problem. Alongside these: a brute-forceable password-reset OTP, a client-side subscription self-grant endpoint, an unverified Apple webhook signature, an optional-and-fails-open RevenueCat webhook secret, a missing ownership check on the recipe-share endpoint, and unbounded SSRF exposure on the recipe-import URL fetch. Every one of these is described with an exact fix in its route section and consolidated in Prioritized Recommendations below.

**What will actually determine 5,000-concurrent-user behavior:** the connection budget, not any single route's logic. HikariCP is capped at 20 connections app-wide, and every controller shares it. The report's clearest, most actionable scale finding is that **every authenticated request pays for 3-4 redundant `findByEmail`/blacklist-check DB round trips** before a controller even runs (duplicated across `JwtAuthenticationFilter`, `JwtService`, and `SubscriptionRequiredFilter`) — `GET /user/me`, almost certainly the single highest-traffic route in the app, triples this cost. Layered on top: several AI- and payment-verification routes hold a Hikari connection open for the full duration of an external call that can legally take up to 120 seconds (`generate-ai-recipes`, `recipes/import`, `subscriptions/verify-receipt`), meaning as few as ~20 concurrent slow AI calls could saturate the *entire* pool and start blocking unrelated requests app-wide. Rate limiting — the one obvious backstop against exactly this kind of burst — currently covers only 4 of 169 routes, not the "global 200/min" backstop the code comments imply. None of this requires new infrastructure to fix; it requires decoupling a handful of external calls from their DB transactions and adding a short-lived cache to the auth path.

**Everything else** — N+1 query patterns (`leaderboard`, `cookbook` add/remove, `mapToResponse` helpers), dead/orphaned routes, two conflicting CORS configs, a few missing DB indexes, GA4 silently fabricating analytics data on misconfiguration, and a mocked payment endpoint that appears to be test scaffolding left wired up — is real but not launch-blocking. It's listed in full under Cross-Cutting Bugs and tiers P1/P2 of the Recommendations section, roughly in the order it's likely to matter.

**Suggested sequencing:** fix the 10 P0 items first (realistically a day or two of focused work, since each is a scoped `@PreAuthorize`/ownership-check/config-verification fix, not a rewrite), do a final check that `JWT_SECRET` and `REVENUECAT_WEBHOOK_SECRET` are actually set in the production Render environment, then decide how much of P1 to take before launch versus fast-follow — the connection-pool/redundant-lookup findings in P1 are the ones worth prioritizing if launch timing forces a choice, since they're the ones a real traffic spike would actually expose.

---

## Infrastructure & Cross-Cutting Concerns

This section covers what actually determines whether the backend survives real concurrent load — far more than any single route's own logic, since almost every route shares the same connection pool, thread pool, and auth pipeline.

### Database connection pool

- HikariCP: `maximum-pool-size=20`, `minimum-idle=5`, `connection-timeout=30000` (30s), `idle-timeout=10000`, `max-lifetime=240000` (`src/main/resources/application.properties:19-24`).
- **20 connections is the hard ceiling for simultaneous DB work across the *entire* application** — every controller, every scheduled job, every `@Async` task. At real concurrency, this is the single most important number in this report: it doesn't matter how well any individual route is written if 20 connections is the shared budget for all of them at once.
- Requests that can't get a connection within 30s throw `SQLTransientConnectionException`, which is not explicitly caught anywhere audited — it would fall through to `GlobalExceptionHandler`'s generic `Exception` handler (`exception/GlobalExceptionHandler.java:119-126`) and return a generic 500, logged via `System.err`/`printStackTrace` rather than a structured logger.
- `spring.jpa.open-in-view` is not overridden anywhere (not found in `application.properties` or `application.yml`), so it defaults to Spring Boot's default of **`true`**. This means the Hibernate session — and by extension a checked-out-but-idle DB connection — stays open for the full duration of view/response rendering on top of the query itself, in every request that touches JPA-lazy fields during response mapping (which, per the route sections below, is most of them). This quietly extends how long each request holds a connection from the 20-connection pool, and is an easy thing to overlook since it's a global default no one explicitly chose.

### Thread pools

- **Tomcat (request-handling) threads**: no `server.tomcat.threads.max` override found anywhere in `application.properties` → Spring Boot default of **200 max threads**.
- **`@Async` executor** (`config/AsyncConfig.java:14-27`): explicitly tuned — `corePoolSize=20`, `maxPoolSize=100`, `queueCapacity=50000`, with a code comment stating "Optimized configuration for 50k+ users." This is in good shape and shouldn't bottleneck under load; it's used for fire-and-forget work like email sending and push notifications.
- The risk is **not** the async executor — it's the main Tomcat pool combined with routes that synchronously block a request thread on a slow external call (see AI integration section: a single scan/generate call can hold a Tomcat thread for up to ~130 seconds). 200 threads sounds like a lot until you do the math against a route with a 120-second worst-case hold time.

### Rate limiting — narrower than it looks

- Bucket4j + Redis is wired (`config/Bucket4jRedisConfig.java`, `security/RateLimitInterceptor.java`) with what reads like a two-tier design: a strict 5-requests/minute-per-IP bucket for auth endpoints, and a "global" 200-requests/minute-per-IP bucket the code comments describe as being "for scraping, DDoS prevention" (`RateLimitInterceptor.java:33-38,56-62`).
- **In practice, neither bucket is actually global.** `WebConfig.addInterceptors` (`config/WebConfig.java:23-25`) registers `RateLimitInterceptor` with `addPathPatterns("/auth/login", "/auth/forgot-password", "/auth/resend-code", "/auth/verify-email")` — **only those 4 paths**. The "global" 200/min bucket inside the interceptor never fires for any other route, including the AI-backed recipe endpoints, grocery/cookbook/meal-plan routes, or `/support/submit`. **Effectively, ~165 of 169 routes have zero application-level rate limiting.**
- This is a real gap for a public launch: nothing stops a single client (malicious or just a buggy retry loop) from hammering `/recipes/scan` or `/support/submit` at full speed.

### Redis as a dependency

- Redis backs both the rate limiter (Bucket4j) and the Spring Cache layer (`spring.cache.type=redis`, `application.properties:100`, used e.g. by `@Cacheable("explore_cuisines")` in `RecipeServiceImpl.java`).
- Because rate limiting is scoped to only the 4 auth paths (above), a Redis outage would degrade to: login/password-reset/verify/resend-code endpoints failing (since `RateLimitInterceptor.preHandle` has no try/catch around the Lettuce/Redis calls), plus cache misses elsewhere falling through to the DB rather than failing the request. It is **not** a full-backend outage risk the way a truly global rate limiter would be — but auth being unavailable during a Redis blip is still a real, launch-relevant risk worth a documented fallback.

### CORS — two conflicting configurations

- `SecurityConfig.java:115-134` defines a proper `CorsConfigurationSource` bean: explicit origin allowlist (`cookedapp.com`, `admin.cookedapp.com`, `localhost`), `allowCredentials(true)`, exposes the `Authorization` header. This is correctly wired into the Security filter chain via `.cors(Customizer.withDefaults())` (`SecurityConfig.java:49`).
- **Separately**, `WebConfig.addCorsMappings` (`config/WebConfig.java:34-40`) registers a *second*, contradictory CORS policy: `allowedOrigins("*")` with no credentials flag, on `/**`.
- Having two independently-maintained CORS configs in the same app is a real maintainability hazard even though Spring Security's own CORS handling generally takes precedence for secured endpoints — it's easy for someone to edit one and assume it's the only one, or for a future Spring Boot upgrade to change precedence behavior. **Recommend deleting the `WebConfig.addCorsMappings` override entirely** and relying solely on the `SecurityConfig` bean, which is the correct, explicit-origin one.

### JWT / auth

- `jwt.expiration=${JWT_EXPIRATION:900000}` exists in `application.properties:29` (reads as a 15-minute setting) **but it is dead configuration — verified by grepping the entire codebase, nothing ever reads `jwt.expiration`/`JWT_EXPIRATION`.** The actual token-generation code (`security/JwtService.java:44-46`) builds the JWT with no `.setExpiration(...)` call at all, with an explicit comment: `// NOTE: token has no expiration per requirements`. **JWTs never expire, full stop** — the property in `application.properties` is misleading leftover config, not an active control. Confirmed independently by the dedicated Auth/User audit pass below.
- **No refresh-token endpoint was found anywhere in the codebase** (`AuthController.java` has no such route) — consistent with tokens never expiring: there's nothing to refresh. The only ways a token stops working are explicit logout (blacklist) or session revocation via `DELETE /sessions/{id}`. This means **a leaked/stolen token (compromised device, log line, MITM) is valid forever** unless a user proactively revokes it — a material security weakness worth fixing before/soon after launch (add real expiration + refresh-token flow).
- `jwt.secret` has a **hardcoded fallback value committed directly in `application.properties:28`** (`${JWT_SECRET:my_super_secure_secret_key_that_is_long_enough...}`) — a long string, but a *known, public-in-source-control* one. If the `JWT_SECRET` environment variable is ever unset in any deployment (a new environment, a misconfigured staging box, anyone who forks the repo), that deployment silently signs tokens with a secret visible to anyone who's ever read this file. **This should be verified as actually set on the production Render service before launch**, and ideally the code should refuse to start (fail fast) rather than silently fall back to a committed default for something as sensitive as the JWT signing key.
- `JwtAuthenticationFilter.doFilterInternal` (`security/JwtAuthenticationFilter.java:22-73`) has a structural bug: `filterChain.doFilter(request, response)` is only called *inside* the `if (email != null && SecurityContextHolder...== null)` block (line 70), not after it. `JwtService.extractEmail()` (`security/JwtService.java:28-30`) returns `Claims::getSubject`, which can legitimately return `null` without throwing if a validly-signed JWT simply lacks a `sub` claim. In that specific edge case, `email` is `null`, the outer `if` is skipped, and **`doFilter` is never called — the request hangs** instead of being rejected or passed through as anonymous. Low likelihood (requires a validly-signed-but-malformed token) but a real, fixable correctness bug in the core auth filter.
- `SubscriptionRequiredFilter` (`security/SubscriptionRequiredFilter.java`) runs after the JWT filter on every authenticated, non-public request and independently re-queries `userRepository.findByEmail(email)` (`:59`) to check subscription status — **the same user the JWT filter's `UserDetailsService` already just loaded by email**. This is a redundant DB query on literally every authenticated request in the app, on top of the pool math above. It fails *open* on any internal error ("Allow request to proceed if there's an error (fail open for safety)", `:74-75`) — a deliberate availability-over-strict-enforcement trade-off, not a bug, but worth knowing: a DB hiccup during this specific query silently lets non-paying users through rather than blocking them.
- This filter is effectively a **hard paywall on almost the entire API** — `FREE`/`EXPIRED`/`CANCELLED` users get a 403 `SUBSCRIPTION_REQUIRED` on anything not explicitly listed in `isPublicEndpoint()` (`SubscriptionRequiredFilter.java:103-126`). `CREATOR`/`ADMIN`/`EDITOR` roles always bypass it. Confirm this matches actual product intent (no free tier at all beyond the explicitly-public routes) before launch, since it's enforced centrally and is easy to lose track of.

### Error handling

- `GlobalExceptionHandler` (`exception/GlobalExceptionHandler.java`) is reasonably thorough — dedicated handlers for not-found, validation, auth, access-denied, data-integrity (with user-friendly duplicate-key message mapping), transaction/constraint violations, and a catch-all.
- The catch-all (`handleGeneral`, `:119-126`) does **not** leak stack traces or internal details to the client (returns a generic `"Server error"` message) — good, no information-disclosure issue there.
- It logs via raw `System.err.println` + `ex.printStackTrace()` rather than a structured logger (SLF4J/Logback) — works, but makes log aggregation/alerting on a hosting platform like Render harder than it needs to be, and is inconsistent with the rest of the app's `Logger`/`@Slf4j` usage elsewhere.

### Database migrations

- `spring.jpa.hibernate.ddl-auto=update` (`src/main/resources/application.yml:12`) — the schema is auto-migrated by Hibernate on startup rather than through versioned migration files (no Flyway/Liquibase dependency in `pom.xml`). This works for a small team moving fast, but has no migration history, no rollback path, and real risk of Hibernate inferring a schema change you didn't intend as the entity model evolves. Worth a conscious decision (not necessarily a blocker) before this is running against real user data at scale.

### Actuator exposure

- `/actuator/**` is in the `permitAll()` list (`SecurityConfig.java:83`). Checked `application.properties`/`application.yml` for `management.endpoints.web.exposure.include` — **not set anywhere**, so Spring Boot's default (only `health` and `info` exposed over HTTP) applies. **Verified: no wildcard/sensitive actuator endpoints (env, heapdump, beans, etc.) are exposed.** This is fine as-is.

---

## Third-Party Dependency Inventory

| Service | How it's integrated | Used for |
|---|---|---|
| **PostgreSQL** | JDBC + HikariCP (`org.postgresql:postgresql`) | Primary datastore. Pool capped at 20 connections — see above. |
| **Redis** | `spring-boot-starter-data-redis` + Lettuce | Bucket4j rate-limit counters (4 auth routes only) + Spring Cache backend (`explore_cuisines`, `explore_categories`, etc.). |
| **Markhor AI** (`recipe.markhorsystems.com`) | Plain `RestTemplate`, no SDK, custom `X-Internal-Secret` header auth | Core AI feature: ingredient scanning, recipe generation, recipe-link extraction, trending suggestions. **Genuinely external third-party vendor, not a service this team controls.** See dedicated section below. |
| **OpenAI** | `AiServiceImpl.java` (calls `api.openai.com` directly) | **Dead code** — a second, unused `AiService` implementation; `MarkhorAiServiceImpl` is `@Primary` so this is never actually invoked in production. Its API key property still exists in config with a placeholder default. |
| **Cloudinary** | `cloudinary-http5` SDK | Image hosting/CDN for recipe, profile, and category images; also does on-the-fly OG-image resizing for share links. |
| **Firebase (Admin SDK)** | `firebase-admin` | Server-side push notification delivery (FCM). |
| **Brevo** (email) | Two separate paths: `spring-boot-starter-mail` (SMTP, `smtp-relay.brevo.com`) **and** a hand-rolled REST call to `api.brevo.com/v3/smtp/email` in `EmailServiceImpl.java` | Transactional email (welcome, password reset, support tickets, critical-error alerts, etc.). Worth confirming which path is actually the live one — having both configured is a little confusing. |
| **Google APIs** | `google-api-client`, `google-api-services-androidpublisher`, `google-auth-library-oauth2-http` | Google Sign-In token verification; Android Play Billing/subscription-status verification. |
| **Google Analytics Data API** | `google-analytics-data` | Server-side analytics reads for the admin dashboard. |
| **RevenueCat** | Webhook receiver, no outbound SDK call | Subscription lifecycle events (purchase, renewal, cancellation, billing issues) — see the Auth/Subscription route section. |
| **Instacart** | `RestTemplate` (unconfigured, no timeout) | Shoppable-list deep links from the grocery list. **Currently unused by any client** (mobile/landing_page/frontend) — appears to be dead/unshipped. |
| **Bucket4j** | `bucket4j-core` + `bucket4j-redis` | Rate limiting (see above — narrower in practice than the code implies). |
| **jsoup** | Library, not a hosted service | HTML scraping — both as a fallback recipe-link extractor when the AI service is unavailable, and inside `MarkhorAiServiceImpl`'s own multi-engine web-search chain (Google/DuckDuckGo/Qwant/Mojeek/Bing). |

---

## Routes — Auth / User / Session / Subscription

*Covers `AuthController`, `UserController`, `SessionController`, `SubscriptionController`, `RevenueCatWebhookController`, `WebhookController`.*

### Cross-cutting notes for this group

- **Every authenticated request pays 3-4 extra DB round trips before the controller runs**, against the shared 20-connection Hikari pool: `JwtAuthenticationFilter` calls `blacklistedTokenRepository.existsByToken()` (`JwtAuthenticationFilter.java:39`), then `CustomUserDetailsService.loadUserByUsername` → `userRepository.findByEmail()`, then `JwtService.isTokenValid()` → `isTokenBlacklisted()` → **a second, duplicate** `existsByToken()` call redundant with the first, then `SubscriptionRequiredFilter` does **another** `userRepository.findByEmail()` to re-check subscription status (`SubscriptionRequiredFilter.java:59`) — the same finding already documented in the Infrastructure section above, now confirmed from the auth-code side with the extra detail that the blacklist check itself is duplicated, not just the user lookup. All four lookups hit indexed columns individually, but at 5,000 concurrent users this is 3-4x the connection pressure per request across the entire authenticated surface. This is the single biggest scale risk found anywhere in this audit and isn't specific to one route.
- Roles are derived live from the DB on every request (`User.java:411-412`, `getAuthorities()`), not baked into the JWT — good for revoking admin access quickly, but combined with tokens that never expire (see Infrastructure section) and the hardcoded JWT-secret fallback, a leaked token for any email is a *permanent* skeleton key if that account is later promoted to `ADMIN`.
- Rate limiting only covers `/auth/login`, `/auth/forgot-password`, `/auth/resend-code`, `/auth/verify-email` (confirmed again independently here, matching the Infrastructure section) — every other route in this group, including `/auth/register` and `/auth/verify-reset-code`, has **zero** rate limiting.

### `AuthController` (`/auth`) — all 9 routes public, all consumed by mobile; several shared with the admin dashboard's own login/reset flow

| Route | Finding |
|---|---|
| `POST /auth/register` | Moderate (100-500ms), dominated by a **synchronous** Brevo OTP email call (`AuthServiceImpl.java` — not `@Async`, unlike push notifications). No password-strength validation anywhere. **No rate limiting** — an attacker can spam OTP emails (Brevo quota cost) or enumerate registered emails via the "already associated with an account" error message. AI recipe generation on signup is correctly `@Async` and off the request path. |
| `POST /auth/verify-email` | Fast, no external calls. OTP is 6 digits (~900k values), no attempt counter of its own, but is covered by the 5/min-per-IP auth bucket — meaningfully slows but doesn't prevent a distributed/IP-rotating attacker. |
| `POST /auth/resend-code` | Has its own 3-strikes/1-hour lockout (good defense-in-depth) on top of the shared bucket. Same synchronous-Brevo cost as register. |
| `POST /auth/login` | LOCAL login: fast (100-300ms). **Apple sign-in fetches Apple's live JWKS on every single login with no caching** (`AuthServiceImpl.java:666-667`, `restTemplate.getForObject("https://appleid.apple.com/auth/keys", ...)`) — using the shared `AppConfig` RestTemplate's 10s/120s timeouts, a degraded Apple endpoint could hold a request (and, since `login` is `@Transactional`, a Hikari connection) for up to ~130s. Covered by the 5/min auth bucket. Apple JWT signature verification itself is done correctly (issuer/audience/RSA signature all checked, `:709-726`). **Fix: cache Apple's JWKS** (rotates infrequently — a 24h in-memory cache removes this network call from the hot path entirely). |
| `POST /auth/logout` | Fast today, but `DeviceSession.token` has **no unique/index constraint** (unlike `BlacklistedToken.token`, which is indexed) — `findByToken` here will degrade toward a table scan as `device_sessions` grows into the tens/hundreds of thousands of rows. **Add an index.** |
| `POST /auth/forgot-password` | Same lockout-protected pattern as resend-code, same synchronous-Brevo cost, same email-enumeration nuance (`ResourceNotFoundException` on a nonexistent identifier reveals account existence). |
| `POST /auth/verify-reset-code` | **Real account-takeover vulnerability.** Missing from `RateLimitInterceptor`'s path list *and* has no attempt-counter/lockout logic of its own (unlike its `resendCode`/`forgotPassword` siblings, which track `resendCount`+`lockoutUntil`). The reset OTP is the same ~900k-value 6-digit code, valid 15 minutes — **completely unthrottled**, meaning an attacker who knows a victim's email/phone can brute-force the reset code and take over the account via the next endpoint. This route is shared by `landing_page`'s own admin password-reset flow (`OtpPage.jsx`), so the exposure covers **both end-user and admin/editor accounts**. **Highest-priority fix in this entire section** — add this path to `RateLimitInterceptor` and/or add the same lockout pattern already used elsewhere in this same controller. |
| `POST /auth/reset-password` | Public, no rate limiting of its own — but its real exposure is inherited entirely from the unthrottled `verify-reset-code` step above, since it requires that step's `"VERIFIED"` sentinel first. No password-strength validation. |
| `POST /auth/apple/callback` | Pure string/URL templating, no DB, no I/O — a thin bridge re-emitting Apple's OAuth redirect into an Android `intent://` deep link (actual verification happens later via `/auth/login`). Properly escapes reflected query params before interpolating into an inline `<script>`. `Cache-Control: no-store` set correctly. No concerns. |

`/auth/google` and `/auth/apple` appear in `SecurityConfig`'s `permitAll` list but are **not implemented as actual routes** in `AuthController` — dead/unused entries (social login is unified through `/auth/login` with a `provider` field); worth removing to avoid confusion.

### `UserController` (`/user`)

- **`GET /user/me`** is almost certainly the single highest-traffic route in the app (called on every app-open/profile-load) and **independently recomputes the exact same subscription-active check `SubscriptionRequiredFilter` just computed moments earlier for the same request** — meaning one call to `/user/me` costs 3 near-identical `findByEmail`-plus-subscription-check passes (JWT filter, subscription filter, and the controller itself) against the 20-connection pool. **Best single candidate in the whole codebase for a short-TTL (few-second) cache** to collapse these into one lookup.
- **`POST /user/sync-subscription` is a genuine, exploitable vulnerability**, not just a scale note: the endpoint accepts an **unvalidated, untyped `Map<String,Object>` body** (`UserController.java:88`, no DTO, no `@Valid`) and `UserServiceImpl.syncSubscription` writes whatever `isActive`/`expirationDate`/`productId` it's given directly onto the user's subscription fields, with **no server-side verification against RevenueCat, Apple, or Google**. Any authenticated user can `POST {"isActive": true, "expirationDate": "2099-01-01T00:00:00", "productId": "yearly_sub"}` to this endpoint and grant themselves premium for free — no secret, no webhook forgery needed, just a normal authenticated request against their own account. **Fix before launch: either remove this endpoint (if the RevenueCat webhook + `/subscriptions/verify-receipt` already cover the legitimate case) or make it advisory/log-only rather than authoritative.**
- `PUT /user/me`, `PUT /user/me/preferences`, `PUT /user/password-reset`, `DELETE /user/me`, `POST /user/send-welcome-email` all follow the same pattern as the rest of the app: correct self-scoping via `authentication.getName()`, synchronous Brevo confirmation emails (blocking, not `@Async`, same recurring pattern as the Auth routes), and no password-strength policy anywhere in the app (register/reset/change-password all skip it).
- `PUT /user/me/preferences` has **no Bean Validation on `UpdatePreferencesRequest` at all** — unbounded free-form lists/strings accepted as-is (e.g. an attacker could submit thousands of "allergies" entries). Low severity but easy to cap.
- `POST /user/profile-photo` is the **slowest route in this controller** — synchronous Cloudinary upload with **no configured client timeout** (`CloudinaryServiceImpl.java:30-36` builds a fresh client per call with no timeout options) plus a synchronous Brevo email afterward, and no file-size/type validation before forwarding to Cloudinary (only "not empty" is checked) — a large/malicious upload reaches Cloudinary before any local rejection.
- `DELETE /user/me` (and the admin equivalent `DELETE /user/admin/{id}`) has a real, if partially optimized, **per-recipe write loop** on account deletion: the "find matching twin recipes" step was already fixed to a single batched query (per an in-code comment), but each twin-recipe still costs up to 4 extra writes + 1 native delete, and each non-twin recipe costs 1 `save()` — meaning deletion time scales with a power user's recipe count. Not a concern for typical users; worth watching for creator accounts with large catalogs.
- Admin routes (`/user/client`, `/user/creators`, `/user/admin`, `/user/editors`, `/user/{id}/role`, `/user/{id}/subscription`, `/user/{id}/status`) are all correctly `@PreAuthorize("hasRole('ADMIN')")`-gated, admin-only (no mobile/frontend usage), and low-volume. Two consistent minor gaps worth a cheap fix: (1) `users.role` has **no dedicated DB index**, so `findAllByRole`/`findAllByRoleIn` (used by `/user/client`, `/user/creators`, `/user/admin`, `/user/editors`) become filtered table scans as the user base grows; (2) role changes and manual subscription overrides (`POST /user/{id}/role`, `PUT /user/{id}/subscription`) are **not written to the activity-log audit trail**, unlike most other mutating admin actions — a role escalation to `ADMIN` should probably be auditable.
- `PUT /user/{id}/status` (block/archive a user) has no visible mechanism forcing an already-issued, never-expiring JWT to stop working for a newly-`BLOCKED` user — worth explicitly confirming `User`'s `UserDetails` implementation (`isEnabled()`/`isAccountNonLocked()`) actually reads `Status`, since if it doesn't, blocking a user via the admin panel doesn't revoke their existing session.

### `SessionController` (`/sessions`) — the one clean spot in this section

Both routes (`GET /sessions`, `DELETE /sessions/{id}`) are fast, low-volume, and — notably — `DELETE /sessions/{id}` has an **explicit, correct ownership check** (`SessionServiceImpl.java:54-56`) preventing one user from revoking another's session by guessing a UUID, which is not something every route in this codebase gets right (see the `Map<String,String>`-body admin routes above). Given tokens never expire, this is currently the *only* way to invalidate one specific stolen token short of a full logout — worth making more prominent in the app's security-settings UI.

### `SubscriptionController` (`/subscriptions`)

- **`POST /subscriptions/pay` is mocked/test code, not a real payment integration**, despite looking like one: it checks a `"tok_fail"` sentinel for a forced-failure test path, then does a **hardcoded `Thread.sleep(1000)`** (`SubscriptionServiceImpl.java:111`) *inside an open `@Transactional` block*, then unconditionally activates a real subscription with `stripePaymentId = "simulated_" + token`. There is no actual Stripe (or any processor) call anywhere in this method — `stripeToken` is caller-controlled and never validated. Any authenticated user could call this with any string and get free premium. **The mobile client defines a method that calls this but nothing in the shipped UI invokes it** — likely unreachable from the app today, but the raw HTTP endpoint is still callable directly by anyone with a valid token. At real concurrency, 20 simultaneous calls to this one endpoint would fully saturate the entire 20-connection Hikari pool for a full second doing nothing. **Needs a product decision: finish the real integration or remove/gate it before launch** — as-is it's both a monetization bypass and a self-inflicted DoS lever.
- **`POST /subscriptions/verify-receipt`** (the real IAP verification path) uses its **own raw `new RestTemplate()`** (`SubscriptionServiceImpl.java:64`) instead of the shared, timeout-configured `AppConfig` bean — meaning Apple/Google calls here have **no bounded timeout at all** (JDK defaults, effectively unbounded), made worse by running inside `@Transactional` (holds a Hikari connection for the call's full duration). A hung Apple/Google endpoint here is a plausible pool-exhaustion trigger for the *entire app*, not just this route. It's also **fail-open on misconfiguration**: missing Google service-account credentials, a Google API exception, or a missing Apple shared-secret all fall back to `isValid = true` (`:194-205, 602-605`) rather than rejecting — a defensible UX trade-off in isolation, but it means a forgotten production env var silently turns into "every receipt is accepted as valid." **Fix: swap in the shared timeout-configured RestTemplate; make the fail-open defaults loud (alert/log) rather than silent.**
- **`GET /subscriptions/paywall-config`** — public, unauthenticated, hit on every paywall render — **writes to the database on every single GET request** via `updateVariantLanguage()` unconditionally re-`save()`ing a hardcoded, never-changing copy string (`PaywallService.java:59-73`). At a realistic 5,000-concurrent-user paywall spike (post-marketing-push or mass trial-expiry), this turns a read-only-looking public endpoint into pure write amplification against the shared pool, for zero functional benefit. **Cheap, high-value fix: only write when something actually changed, or don't persist static copy as a mutable row at all.**
- `GET /subscriptions/me`, `GET /subscriptions/history`, `GET /subscriptions/plan` are all fast, correctly scoped, low-risk; `/plan` is public (appropriate for a pricing page) and a good caching candidate since it rarely changes. `/history` has no pagination but realistic per-user row counts are small.
- `GET /subscriptions/status` (marked "legacy" in its own `@Operation` summary) is **confirmed dead code** — no references anywhere in `mobile`, `landing_page`, or `frontend`. Candidate for removal.

### `RevenueCatWebhookController` (`/subscriptions/revenuecat-webhook`, aliased `/webhooks/revenuecat`)

**Real vulnerability, config-dependent.** The webhook's shared-secret check (`revenuecat.webhook.secret`, backed by `REVENUECAT_WEBHOOK_SECRET`) **defaults to an empty string if the env var isn't set, and the validation logic explicitly skips the entire authorization check when the secret is blank** (`RevenueCatWebhookController.java:60-72`). If `REVENUECAT_WEBHOOK_SECRET` is ever left unset in production, this endpoint becomes a **completely open way to grant any known user (by email or UUID) an active premium subscription**, via a crafted `INITIAL_PURCHASE`/`RENEWAL` payload — no real payment involved. Even when the secret *is* set, the comparison is a plain non-constant-time string compare (minor timing-attack surface), and there's no replay protection (no event-ID/nonce dedup). **Action item before launch: verify `REVENUECAT_WEBHOOK_SECRET` is actually set in the production Render environment, and change the code to fail closed (401) rather than silently accepting everything when unconfigured.**

### `WebhookController` (`/webhooks`)

- **`POST /webhooks/apple` — critical, unconditional vulnerability, not config-dependent.** `JwsDecoder.decode()` (`SubscriptionServiceImpl.java:792-804`) only **base64-decodes** the JWS payload — it never verifies the signature against Apple's certificate chain, unlike the correctly-implemented RSA verification already used for Sign-in-with-Apple in `/auth/login`. Since `originalTransactionId` isn't inherently secret, **anyone able to obtain or guess one can POST an unsigned/self-forged payload claiming `DID_RENEW`/`SUBSCRIBED` with an arbitrary far-future expiry, and the backend will trust it and activate the subscription** — no cryptographic gate at all, in any environment. This is a more serious version of the RevenueCat-secret issue above because there's no configuration toggle that could ever fix it — the verification step is simply never implemented. **Fix before launch: implement real JWS signature verification against Apple's published certificates, or at minimum treat this webhook as advisory and re-verify via `verify-receipt`/App Store Server API before granting entitlements.**
- `POST /webhooks/google` is meaningfully safer — it **does** re-verify the notification against the real Google Play Developer API before trusting it, which bounds the practical impact of a forged request (an attacker still needs a `purchaseToken` Google itself recognizes as a genuine, valid purchase). The remaining gap: the handler reads **no OIDC bearer token from Google's Pub/Sub push request** (Google supports and recommends this for exactly this purpose), so while a forged notification can't grant a *new* fraudulent subscription, it could replay/trigger a resync for a purchase token belonging to another real user.
- Both webhook handlers **always return `200 OK` regardless of internal outcome**, by design ("avoid retries" per the code comments) — reasonable for genuinely non-actionable notifications, but it also means a transient DB error during processing is indistinguishable from success and **is silently dropped with no retry from Apple/Google**, since their retry behavior depends on seeing a non-200.

### Summary of this slice's top findings

1. **`POST /webhooks/apple`**: Apple's payload is decoded but never signature-verified — an unconditional, environment-independent path to fraudulent subscription activation.
2. **`POST /subscriptions/revenuecat-webhook`**: auth is optional and fails open if `REVENUECAT_WEBHOOK_SECRET` is unset — verify it's actually configured in production.
3. **`POST /auth/verify-reset-code`**: no rate limiting, no lockout, brute-forceable 6-digit reset code — affects both end-user and admin password resets.
4. **`POST /user/sync-subscription`**: trusts an arbitrary client-supplied body to grant premium — any authenticated user can self-serve free access.
5. **`GET /subscriptions/paywall-config`**: unconditional DB write on every call to a public, high-traffic, unauthenticated route.
6. **`POST /subscriptions/pay`**: hardcoded 1-second blocking sleep inside an open transaction, plus a non-functional mocked "payment" that grants real access — needs a product decision before it's reachable at scale.
7. **`POST /subscriptions/verify-receipt`**: uses an un-timed raw `RestTemplate` for external Apple/Google calls inside an open transaction — a slow third party here can exhaust the shared connection pool for the whole app.
8. **Cross-cutting**: JWTs never expire and the signing secret has a hardcoded, source-visible fallback (see Infrastructure section) — combined with #3 and #4 above, a compromised token or a config mistake has an unusually long blast radius in this app.

---

## Routes — Recipes / Content / AI

*Covers `RecipeController`, `AdminRecipeController`, `RecipeAssignmentController`, `RecipeDataController`, `IngredientController`, `SavedIngredientController`, `ExploreSeederController` — 68 routes.*

### Cross-cutting notes for this group

- **Shared external-call timeout**: the `AppConfig` RestTemplate bean (`connectTimeout=10s`, `readTimeout=120s`, comment "120s for AI") is injected into `MarkhorAiServiceImpl` (AI recipe generation/import/scan/search), `RecipeDataServiceImpl` (DALL·E image generation), and `VisionImageRankerServiceImpl` (GPT-4o-mini vision) — **every AI-backed route in this group can legally hold a request thread, and if `@Transactional` a Hikari connection, for up to 120 seconds.**
- **A second paywall allowlist that diverges from `SecurityConfig`'s own permitAll list**: `SubscriptionRequiredFilter`'s own public-endpoint list (`SubscriptionRequiredFilter.java:103-126`) exempts `/api/recipe-data`, `/api/ingredients`, `/recipes/popular`, `/recipes/explore`, `/recipes/top-creators`, `/recipes/trending-ai` — but **not** `/ingredients/**` (`SavedIngredientController`'s base path, distinct from `/api/ingredients`) or `/api/admin/**`/`/admin/seed/**`. Practical effect: a `Role.USER` with no active subscription is paywalled out of basic personal-recipe CRUD *and* out of saving/searching ingredients — worth a deliberate product check that this matches intent (no free tier beyond the explicitly-public Explore/Popular/Trending feeds).
- **Caching**: Redis, 60-minute TTL (`config/CacheConfig.java`) backs `exploreRecipes`, `popularRecipes`, `explore_cuisines`, `explore_categories`, `trendingDishes`; evicted `allEntries=true` on most admin recipe/category writes.
- **Pagination**: confirmed present via `PaginationUtils.clampSize` (max 100) on every paginated endpoint in `RecipeController`/`AdminRecipeController`/`RecipeAssignmentController`. One real behavioral side effect: `landing_page`'s `DataContext.jsx:161` requests `GET /api/admin/recipes?size=40000` expecting "all recipes in one page" — the clamp now silently returns only the first 100 instead of ~40000, no crash, but the admin app's "cache everything client-side" assumption is now silently wrong. **Worth flagging to the landing_page team as a follow-up** (they need either paginated loading or a dedicated bulk-export endpoint).

### `RecipeController` (`/recipes`) — the AI-heavy, user-facing core

| Route | Finding |
|---|---|
| `POST /recipes/import` | **Real SSRF vulnerability.** The only validation on the client-supplied `url` is "non-blank" plus an `https://` auto-prefix (`RecipeController.java:38-44`, `MarkhorAiServiceImpl.java:460-463`) — no allow/deny-list for private/internal hosts (`169.254.169.254`, `localhost`, RFC1918 ranges). The Jsoup fallback path (`followRedirects(true)`, no host restriction, `MarkhorAiServiceImpl.java:509-670`) fetches the URL directly and reflects title/image/ingredients back in the response — a server-side fetch-and-leak primitive. Also one of the two slowest routes in the backend (up to 120s AI path or 10s scrape path), `@Transactional` for the full duration — a real Hikari-pool-exhaustion candidate under a burst of imports. **Fix: add a private-IP/localhost blocklist before this URL fetch runs.** |
| `POST /recipes/detect-ingredients`, `/recipes/scan`, `/recipes/scan-typed` | All AI-vision/generation routes, correctly paywalled behind `verifyAiAccess` (402 if no AI access). All slow (1-5s typical, up to 120s worst case). `/scan` and `/scan-typed` both run a **synchronous** (not async) per-recipe image-matching pass with up to 4 sequential DB queries per result recipe, one of which (`findRandomPopularRecipes`) uses `ORDER BY RANDOM()` — a full-scan-and-sort pattern that will slow down as the Explore table grows. No file-size/type validation before forwarding uploads to the paid external AI service. |
| `POST /recipes/generate-ai-recipes` | **The single most AI-latency-exposed route in the backend.** `@Transactional` for the *entire* duration including the external AI call (up to 120s) — at just ~20 concurrent slow generations, the entire 20-connection Hikari pool is exhausted, blocking unrelated requests app-wide. Also does a nested per-recipe × per-ingredient DB-write loop, and fires an `@Async` image-matching job per saved recipe that itself does an unbounded `imageLibraryRepository.findAll()`. No cap on submitted ingredient-list size. **Fix: move the AI call outside the `@Transactional` boundary** (call first, persist after in a short transaction). |
| `POST /recipes/validate-typed-ingredients` | Local-only (no AI call, no subscription check by design), but has **no upper bound on list size** — each item is 1-2 DB round trips, so a large array is a minor DoS/cost vector. |
| `POST /recipes` (create) | Used by **both** mobile (personal recipes) and `landing_page`'s admin dashboard (to create EXPLORE-track recipes as the logged-in admin/editor) — same endpoint serving two different product intents. Otherwise clean: correct self-scoping, no external calls. |
| `GET /recipes` (own recipes), `GET /recipes/{id}` | Correct ownership checks throughout. `GET /recipes` has **no pagination at all** and its `@EntityGraph` doesn't cover `recipeIngredients`, so mapping lazy-loads that collection per recipe — fine for typical users, a real N+1 for power users with large libraries. |
| `DELETE /recipes/{id}`, `PUT /recipes/{id}/visibility`, `PATCH /recipes/{id}/pin` | All correctly ownership-checked. **`/visibility` appears unused/orphaned** by all three frontends — the mobile app instead relies on `/share` (below) to make a recipe public, and no discovered UI path makes a public recipe private again. |
| `GET /recipes/{id}/share` | **Real IDOR vulnerability.** This is a side-effecting GET (flips `isPublic=true` if not already) with **no ownership check at all** — `findById` only (`RecipeServiceImpl.java:693-705`), unlike every sibling mutation in this controller (`delete`, `togglePin`, `togglePublicVisibility`), which all correctly compare `recipe.getUser().getEmail()` to the caller. **Any authenticated user can force any other user's private recipe public by guessing/enumerating its UUID.** Fix: add the same ownership check used everywhere else in this controller. |
| `GET /recipes/explore`, `/explore/cuisines`, `/explore/categories`, `/recipes/trending-ai` | Public, cached (60-min Redis TTL), single indexed queries — no concerns, good examples of decoupling AI/aggregate latency from the request path. |
| `GET /recipes/top-creators` | Public, **the only high-traffic aggregate query in this group with no `@Cacheable`** — a correlated-subquery `GROUP BY`, uncached, hit by any anonymous visitor. Add caching to match the pattern used everywhere else. |
| `GET /recipes/popular` | Public, cached, but the **cache key includes the user's email** for logged-in callers — meaning the cache is effectively per-user for authenticated traffic, not shared, so each logged-in user's first request per hour is a cache miss hitting the same `ORDER BY RANDOM()` query. At 5,000 concurrent logged-in users this significantly blunts the caching benefit relative to `/explore`. |
| `GET /recipes/web-search` | The single most failure-prone/latency-variable route in the backend: a **sequential cascade of up to 5 different search-engine scrapes** (Google→DuckDuckGo→Qwant→Mojeek→Bing), each with its own 8-10s timeout — worst case ~40-50s if the first four all fail, entirely dependent on third-party sites' anti-bot behavior remaining stable. No SSRF risk here (hardcoded target hosts, user only controls the query string). |
| `GET /recipes/imports`, `PUT /recipes/{id}/validate` | Clean, fast, correctly scoped — no concerns. |

### `AdminRecipeController` (`/api/admin/recipes`, ADMIN/EDITOR)

- `PUT /api/admin/recipes/{id}` and `DELETE`/`restore`/`status` all allow **any EDITOR to mutate any recipe**, not scoped to their own assignments — bypasses the `RecipeAssignmentController` workflow entirely if an editor knows/guesses an ID. Likely intentional for a shared editorial tool, worth confirming.
- `PUT /api/admin/recipes/{id}` does a synchronous Cloudinary upload when an image is attached (slow: 0.5-3s+) and evicts **all** explore/popular/taxonomy caches (`allEntries=true`) on every save — a burst of admin edits repeatedly stampedes the public-facing caches.
- `POST /api/admin/recipes/bulk` has a **controller/service inconsistency**: `@PreAuthorize` allows EDITOR, but the service layer hard-requires `Role.ADMIN` (`RecipeServiceImpl.java:70-72`) — an EDITOR passes the security check and then gets a confusing 400. No batch-size cap.
- `PUT /api/admin/recipes/bulk-status` is genuinely well-implemented (`findAllById`/`saveAll`, not a loop) — worth using as the template for a future bulk-delete, since today's `DELETE /api/admin/recipes/{id}` bulk-delete UX in `landing_page` issues **N sequential DELETE requests** client-side.

### `RecipeAssignmentController` (`/api/admin/recipes`, editorial workflow)

- **`GET /api/admin/recipes/leaderboard` is the clearest N+1 in this entire audit**: loads all EDITOR users, then does **6 separate count queries per editor** — `O(6×editor-count)` DB round trips per request. Degrades linearly with team headcount; fix with one grouped `GROUP BY assigned_to_user_id, status` query instead.
- `getAllAssignments`/`getAssignmentsByStatus`/`getMyAssignments` all share a `mapToResponse` helper that does a **redundant `recipeRepository.findById` per assignment row** instead of using the already-loaded `a.getRecipe()` association — up to ~100 avoidable extra `SELECT`s per page at the sizes `landing_page` actually requests (`size=1000`, clamped to 100).
- `broadcastStats()` (which internally re-runs the full 8-query stats dashboard) is called synchronously after nearly every write in this controller (create/batch-assign/status-update/submit/validate/reject/reassign/remove) — every single-row mutation in the editorial workflow also re-executes the entire stats query set. Worth making async/debounced.
- `PUT .../{id}/validate` (admin approves a submission, which also publishes the recipe) does **not** go through the cache-eviction-annotated publish path — a newly-validated recipe won't appear in `/recipes/explore`/`/recipes/popular` for up to the full 60-minute cache TTL. Fix: route through the same `@CacheEvict` used elsewhere.
- `PUT .../{id}/status` has **no ownership check** — any EDITOR can change the status of any assignment, not just their own (contrast with `submitForValidation`, which correctly checks ownership).
- Several routes are confirmed **unused/orphaned** by all three frontends: `PUT .../reassign`, `DELETE /api/admin/recipes/assignments/{id}`, `GET .../{id}/history` — candidates for removal or a deliberate "keep for future UI" decision. `recipe_assignments` also has no explicit DB index on `status`/`assigned_to_user_id`, worth adding as the table grows.

### `RecipeDataController` (`/api/recipe-data`) — **the single most serious finding in this audit**

> **Every route in this controller is fully unauthenticated** — `/api/recipe-data/**` is in `SecurityConfig.permitAll` (`:73`) *and* on `SubscriptionRequiredFilter`'s own allowlist, and **not one method has `@PreAuthorize` or any other access check** (confirmed by reading the full controller — no `@PreAuthorize` import even appears in the file). This is clearly meant to be an internal admin-tooling API (its only real consumer is `landing_page`'s `RecipeSubmission.jsx`/`PutRecipePage.jsx`) but the security config doesn't reflect that at all.

- **`POST /api/recipe-data` and `POST /api/recipe-data/bulk`: unauthenticated, billed OpenAI cost-amplification ("denial of wallet") attack.** If the caller omits an `image`, the backend calls **OpenAI DALL·E 3** server-side (`RecipeDataServiceImpl.java:91-115`, ~$0.04-0.08/image, billed to this app's own OpenAI account) with **zero auth, zero rate limit, zero per-caller attribution**. The `/bulk` variant multiplies this by an uncapped `names` list — a single unauthenticated HTTP request can trigger dozens of billed image generations.
- **`DELETE /api/recipe-data/bulk`: unauthenticated, complete-catalog wipe.** `deleteAllById(ids)` with no auth — and since `GET /api/recipe-data` (also public) returns every ID, an anonymous caller can harvest all IDs and wipe the entire catalog in two requests.
- **`POST /api/recipe-data/bulk-update-images`: unauthenticated write into the live, user-facing `Recipe` table**, not just the `recipe_data` staging table — anyone can overwrite an existing recipe's `image` field by uploading a file named after that recipe. Also a slow, sequential, `@Transactional`-wrapped per-file Cloudinary-upload loop.
- The remaining routes (`GET`/`PUT`/`DELETE` single-item, `pending-images`, `sync-status`) share the same missing-auth problem at lower individual severity (read exposure, single-item mutation).
- **Fix before launch, this whole controller together**: add `@PreAuthorize("hasAnyRole('ADMIN','EDITOR')")` (or a service-to-service secret) across every route here — it's the highest-priority action item in this entire report.

### `IngredientController` (`/api/ingredients`) vs `SavedIngredientController` (`/ingredients`) — easy to confuse, different auth models

- `GET /api/ingredients` returns the **entire ingredients table, unpaginated, uncached, on every call** — and that table only grows (every recipe-create/import/generate/admin-bulk-import path can mint new ingredient rows). Fine today; the clearest "will get slow as data grows, no mitigation in place" endpoint in this whole group. It's currently only consumed by `landing_page`, so adding pagination/caching proactively is low-risk.
- `PUT /api/ingredients/{id}` throws a bare `RuntimeException` on a missing ID, which the global handler maps to **HTTP 500 instead of 404** — same class of minor bug as elsewhere in this codebase; swap for the app's typed `ResourceNotFoundException`.
- `SavedIngredientController`'s base path `/ingredients` is **not** on `SubscriptionRequiredFilter`'s allowlist (unlike `/api/ingredients`) — every route there (search, save, list, delete) is paywalled behind an active subscription for non-staff users, consistent with the cross-cutting paywall-scope note above.
- `GET /ingredients/search` loads **all** matches for a query and sorts them in Java before truncating to 20 (rather than pushing relevance ordering + `LIMIT` into SQL) — proportional to total matches, not the 20 returned; will degrade as the ingredient table grows. Ownership checks elsewhere in this controller (`DELETE /ingredients/saved/{id}`) are correctly implemented.

### `ExploreSeederController` (`/admin/seed`) — no `@PreAuthorize` on 2 of 3 routes, one of them destructive

- **`POST /admin/seed/explore/reset` is the most dangerous route found in this entire audit from a pure blast-radius standpoint.** It performs a real, hard `@Modifying` bulk `DELETE FROM recipes WHERE origin='EXPLORE'` (`ExploreSeederController.java:39`) — **permanently wiping the entire public Explore catalog** — then calls a reseed method that is **dead code** (`ExploreDataSeederServiceImpl.java:31-33` starts with `if (true) return;`), so **nothing is restored afterward.** Neither this route nor its sibling `POST /admin/seed/explore` has an `@PreAuthorize` check — **any authenticated user with an active paid subscription (not just staff) can wipe the entire Explore catalog with one request**, with no recovery path through the API. Not currently called by any known frontend, but that's the only thing currently limiting exposure. **Fix immediately: add `@PreAuthorize("hasRole('ADMIN')")`, and either restore real reseeding or remove the destructive clear-without-restore behavior.**
- `POST /admin/seed/image-library-backfill` (the one route in this controller with a correct `@PreAuthorize`) loads the **entire `recipes` table into memory**, then makes a **synchronous OpenAI GPT-4o-mini vision call per untagged image** — for an established catalog this is realistically minutes-to-tens-of-minutes of wall-clock time on one synchronous HTTP request, held inside `@Transactional` the whole time (one Hikari connection pinned for the full duration), and almost certainly longer than any reverse-proxy/load-balancer timeout on Render — the client would see a timeout while the server-side operation (and its OpenAI spend) keeps running regardless. **Should be restructured as an `@Async` background job with a status-polling endpoint**, using the app's existing async infrastructure.

### Summary of this slice's top findings

1. **`RecipeDataController` (`/api/recipe-data/**`) is entirely unauthenticated** — billed DALL·E generation, catalog bulk-delete, and arbitrary live-`Recipe`-image overwrite are all reachable by anyone on the internet. **Top-priority fix in this whole report.**
2. **`POST /admin/seed/explore/reset`** permanently deletes the entire public Explore catalog with no reseed and no role check — any paying (non-staff) user could trigger it today.
3. **`GET /recipes/{id}/share`** has no ownership check — any user can force any other user's private recipe public by ID.
4. **`/recipes/import`** has no SSRF protection on the user-supplied URL it fetches server-side.
5. **`GET /api/admin/recipes/leaderboard`** (`O(6×editor-count)` N+1) and the shared `mapToResponse` redundant-`findById` pattern across all assignment-list endpoints are the clearest performance N+1s found in this audit.
6. The `SubscriptionRequiredFilter` paywall allowlist diverges from `SecurityConfig`'s permitAll list, silently paywalling basic personal-recipe CRUD and all ingredient-saving behind an active subscription — worth a deliberate product confirmation.
7. `POST /recipes/generate-ai-recipes`, `/recipes/import`, and `POST /admin/seed/image-library-backfill` are the routes most likely to exhaust the shared 20-connection Hikari pool under concurrent load, since each can legally hold a connection for up to 120s (or effectively unbounded for the backfill loop).

---

## Routes — Meal Plans / Grocery / Cookbooks / Categories

### Cross-cutting notes for this group

- Every route in `MealPlanController`, `GroceryItemController`, `CookbookController`, and `AdminCategoryController` requires a valid JWT (none are in `SecurityConfig`'s `permitAll()` list) and — for the first three, which are user-owned resources — is gated by the `SubscriptionRequiredFilter` hard paywall described above. None of `/meal-plans/**`, `/grocery-items/**`, `/cookbooks/**`, `/api/admin/categories/**` are in that filter's own public-endpoint allowlist, so every request here pays the extra `findByEmail` query the infra section describes.
- **Ownership/IDOR handling is consistently correct** across all `{id}`-based routes in this group: every service method re-verifies `entity.getUser().getEmail()/getId()` against the authenticated caller before returning or mutating. The one consistent nit: a mismatch throws `BadRequestException` → **HTTP 400**, not 403/404 — which means a caller probing another user's ID gets a response that confirms the record exists (an ID-enumeration/existence-oracle issue), even though the actual data is never leaked or modified.

### `MealPlanController` (`/meal-plans`) — **entirely unconsumed**

All 4 routes (`POST /meal-plans`, `GET /meal-plans`, `GET /meal-plans/date/{date}`, `DELETE /meal-plans/{id}`) are fully implemented, reasonably fast (single-digit indexed queries for the reads), and correctly ownership-checked — **but an exhaustive grep of `mobile/lib`, `landing_page/src`, and `frontend/src` found zero calls to any of them.** No `meal_plan_service.dart` exists in mobile (unlike the sibling `grocery_service.dart`/`cookbook_service.dart`). This reads as either dead backend functionality or a feature that was built server-side and never wired up client-side — worth confirming with product/mobile before launch, since it's effectively untested-by-real-traffic code.

One real bug regardless of usage: `POST /meal-plans` creates one `GroceryItem` per recipe ingredient via an **individual `.save()` call inside a loop** (N+1 writes, `MealPlanServiceImpl.java:56-68`) instead of `saveAll()`, and `DELETE /meal-plans/{id}` does not clean up those associated grocery items, leaving them orphaned.

### `GroceryItemController` (`/grocery-items`)

| Route | Notes |
|---|---|
| `POST /grocery-items/instacart` | **Unused by any client.** Calls the Instacart API via a `new RestTemplate()` with **no configured timeout** (default = effectively unbounded wait) — the single highest-severity finding in this group if this route is ever activated: a slow/hung Instacart connection would pin a Tomcat thread indefinitely. Has a graceful fallback URL if the call *fails* cleanly, but nothing bounds a *hung* connection. |
| `POST /grocery-items` | Used by mobile (`grocery_service.dart`). Get-or-create logic for ingredient names has a benign race under concurrent load (two simultaneous requests for a brand-new ingredient name can both miss the existence check) unless a DB unique constraint backs it up — not confirmed either way. **The mobile client fans out one HTTP request per ingredient when adding a whole recipe's worth of items** (`Future.wait` over N calls) rather than calling in batch — a `BatchCreateGroceryItemRequest` DTO already exists in the backend but is never wired to any controller endpoint. This amplifies real request volume well beyond the per-route numbers suggest. |
| `GET /grocery-items` | Clean single-query fetch (proper `@EntityGraph`), used heavily by mobile. **No pagination** — returns the user's entire grocery-item history unless the client prunes it; long-tail users who don't open the app regularly could accumulate large result sets. |
| `GET /grocery-items/date/{date}` | Fast, indexed, correctly ownership-scoped — but **unused**; mobile only ever calls the unfiltered `GET /grocery-items`. |
| `PUT /grocery-items/{id}/toggle` | Fast, correctly ownership-checked, and the mobile client already debounces taps client-side before calling it — no concerns. |
| `DELETE /grocery-items/{id}` | Fast, correctly ownership-checked. Mobile's own client-side 24-48h auto-cleanup logic calls this **once per stale item in a loop** (no bulk-delete endpoint exists) — same request-amplification pattern as the batch-add issue above. |

### `CookbookController` (`/cookbooks`) — **highest real scale risk in this group**

- `POST /cookbooks` and `PUT /cookbooks/{id}` both build their initial/updated recipe set via `recipeIds.stream().map(id -> recipeRepository.findById(id)...)` — **one individual `SELECT` per recipe ID supplied by the client**, directly proportional to client input size, instead of a single `findAllById(recipeIds)` call.
- Both routes' response mapping then walks each recipe's `categories`, `cuisine`, `recipeIngredients[].ingredient`, and creator `user` — all lazy associations **with no `@BatchSize` configured** (`Recipe.categories`/`Recipe.cuisine` in particular) — stacking a second N+1 on top of the first.
- **`PUT /cookbooks/{id}` is the one to prioritize fixing**, because it's not just used for renames: the mobile app implements "add one recipe to a cookbook" and "remove one recipe from a cookbook" by first `GET`-ting the full cookbook, then `PUT`-ting back the *entire* modified recipe-ID list (`cookbook_service.dart` — `addRecipeToCookbook`/`removeRecipeFromCookbook`). That means every single "save recipe to cookbook" tap — likely one of the most frequent interactions in the app — pays both N+1 patterns, scaled by the *cookbook's total recipe count*, not just the one recipe being added.
- `GET /cookbooks` (the cookbook-list/home-screen load, called on or near every app open) has the same response-mapping N+1, though `Cookbook.recipes` itself does have `@BatchSize(50)` set, so only the *nested* associations (categories/cuisine/ingredients/creator) are the residual problem, not the recipe list itself.
- Ownership checks are correct on every `{id}` route (`PUT`, `GET /{id}`, `DELETE`, `PATCH /{id}/pin`).
- `PATCH /cookbooks/{id}/pin` has no server-side limit on how many cookbooks a user can pin simultaneously, if the product intends a cap — currently unenforced.
- **Recommended fix, in priority order:** (1) replace the per-ID `findById` loops with `findAllById(recipeIds)` in both `POST` and `PUT`; (2) add `@BatchSize` to `Recipe.categories`, `Recipe.cuisine`, `Ingredient`, and `User`, or add explicit fetch-join queries for the cookbook read paths; (3) add dedicated `POST /cookbooks/{id}/recipes/{recipeId}` and `DELETE /cookbooks/{id}/recipes/{recipeId}` endpoints so the mobile client can add/remove one recipe without a GET-then-PUT-full-set round trip — this alone would eliminate most of the risk described above for the single most common interaction.

### `AdminCategoryController` (`/api/admin/categories`)

- `GET /api/admin/categories` is **missing `@PreAuthorize`** — its sibling `POST`/`PUT`/`DELETE` all correctly require `ADMIN`/`EDITOR`, but the list endpoint only requires *some* valid JWT, meaning any authenticated user of any role (not just admin/editor staff) can call it. The data itself isn't sensitive, so this is low severity, but it's an inconsistency worth a one-line fix. In practice only the admin dashboard calls it today.
- The recipe-count aggregation query behind this endpoint (`GROUP BY` join across `Recipe`) is a full scan/join with no maintained counter — fine at current data volume, worth watching as the recipe catalog grows.
- `POST`/`PUT` correctly go through the same name-normalization path I already fixed for the taxonomy-aliasing bug (categories created via the admin dashboard now land on the same canonical row that recipe-tagging uses).
- `DELETE` does not pre-check whether recipes still reference the category before deleting — the failure mode is *safe* (a DB foreign-key constraint blocks it, surfaced as a clean 409 by `GlobalExceptionHandler`), just not a friendly message ("N recipes still use this category").

---

## Routes — Admin / Ops / Support / Notifications

*Covers `AdminDashboardController`, `DashboardController`, `AnalyticsController`, `ActivityController`, `ErrorMonitoringController`, `SupportController`, `NotificationController`, `NotificationCampaignController` — 32 routes.*

> **Correction to this section's rate-limiting claims:** an earlier pass over this slice assumed the "global" 200 req/min-per-IP `Bucket4j` bucket (`RateLimitInterceptor.java:33-38`) applies to every request. It does not — see the Infrastructure section above: `WebConfig.addInterceptors` (`config/WebConfig.java:23-25`) only registers this interceptor on 4 auth paths. Confirmed independently by two separate reads of `WebConfig.java`. **Every public route below (`/support/submit`, `/api/analytics/track`, `/notification-campaigns/*/open`, `/notification-campaigns/*/click`) currently has zero application-level rate limiting, not even the 200/min backstop.** This makes the abuse-vector findings below more urgent than originally assessed, not less.

### Consumer-naming note

`landing_page/` is the internal **Admin Dashboard** (React) — not a marketing page despite the folder name. `frontend/` is the actual public marketing website (Angular). `mobile/` is the end-user Flutter app. Several routes in this slice that look "admin-only" by controller name are in fact called directly by real end users — flagged individually below.

### `AdminDashboardController` — `GET /api/admin/dashboard`

Returns 4 raw platform counts (clients, recipes, scans, cookbooks). Admin-gated correctly (`hasRole('ADMIN')`). **Dead code** — no caller found anywhere in `landing_page`/`mobile`/`frontend`; the admin dashboard's actual KPI tile is fed by `GET /api/kpi/global` below. Flag for removal.

### `DashboardController` — `GET /api/kpi/global`

The admin dashboard's main KPI widget (users, premium count, conversion, ARPU, LTV, churn, recent transactions, 6-month revenue chart), **polled every 30 seconds** while an admin has the dashboard tab open (`landing_page/src/pages/DashboardPage.jsx:60`).

- **Missing `@PreAuthorize` entirely** (`DashboardController.java:21-24`) — any authenticated user of *any role* (not just admin) can pull platform-wide revenue, ARPU, LTV, and named recent-transaction customers. This is a real authorization gap on business-sensitive data.
- Loads the **entire `subscription_payments` table into memory with an unpaginated `findAll()`** (`KpiService.java:89`), then does three separate full in-memory passes over it (sum for revenue, sort-and-limit-to-5 for "recent transactions," bucket-by-month for the chart). Classic unbounded-aggregate-in-app-code pattern — fine today, will slow down as that table grows, and it's re-run from scratch every 30 seconds per open admin tab.
- A demo-data seeder (`KpiService.initMocks()`) still runs on every app startup via `@EventListener(ApplicationReadyEvent.class)`, seeding 50 mock payments if the table is empty — a debug hook still wired into production boot.
- **Fix priority: high** — add `@PreAuthorize("hasRole('ADMIN')")`, replace `findAll()` + Java aggregation with SQL `SUM`/`GROUP BY`/`ORDER BY ... LIMIT 5`, and gate `initMocks()` behind a non-production profile.

### `AnalyticsController` (dual-mapped at both `/api/analytics/**` and `/api/app-metrics/**`)

| Route | Notes |
|---|---|
| `POST /api/analytics/track` (+ `/api/app-metrics/track`) | **Public**, called by every real user viewing the paywall (`mobile/lib/services/paywall_service.dart:27`). Accepts an unvalidated free-form `Map<String,String>` with no size/shape constraints — genuinely end-user-facing traffic with currently no rate limit at all (see correction above). |
| `GET /api/analytics/transactions`, `/subscriptions` | Admin-only, correctly `@PreAuthorize`'d, properly paginated via `PaginationUtils`. Fine as-is; `/subscriptions` is misleadingly named (returns *all* CLIENT users, not just active subscribers). |
| `GET /api/analytics/firebase/traffic`, `/events` | Admin-only. Calls Google Analytics Data API (GA4) live per request, no caching; falls back to **synthetic random numbers** derived from the user count if GA4 isn't configured — and `ga4.property.id` defaults to a placeholder value (`123456789`) unless overridden in production, meaning if that env var is missing, this page silently serves fabricated data indistinguishable from real analytics. |
| `GET /api/analytics/firebase/detailed` | **Most expensive single endpoint in this slice.** Up to 4 sequential live GA4 API calls in one request, plus two more unbounded `findAll()` full-table loads (`userRepository.findAll()` for 30-day signups, `paymentRepository.findAll()` for revenue) computed in Java. `retentionDay7`/`retentionDay30` are **permanently hardcoded**, never actually computed — GA4's Cohort API was never implemented. Admin-only, so no end-user concurrency risk, but this is the textbook "needs pre-aggregation/caching before it gets slow" endpoint the launch-readiness question was specifically asking about. |

### `ActivityController`

- `GET /activities` — a user's own activity history. **Genuinely end-user traffic** (`mobile/lib/services/user_service.dart:271`), correctly self-scoped, indexed, paginated. No concerns.
- `GET /activities/editors` — admin oversight view of EDITOR activity. Admin-gated, but notably **not** routed through the same `PaginationUtils` clamp used elsewhere (max 100) — the admin dashboard requests `size=1000` in one call and gets it, since Spring's default page-size ceiling (2000) is much higher than the app's own convention. Minor inconsistency, not a bug.

### `ErrorMonitoringController`

- `POST /errors/critical` — mobile crash/error ingestion. **Not actually public** (requires a valid JWT, despite the name/purpose suggesting otherwise) — but has **zero field validation** on the request DTO (no `@Size` caps on `errorMessage`/`stackTrace`/`context`). Real end-user traffic (`mobile/lib/services/error_monitoring_service.dart:103`). A crash storm from a bad app release would generate one async Brevo alert email *per crash*, with no batching/deduping — worth capping before a real release goes out. Async email means it doesn't block the response, but consumes the shared 20-100-thread async pool.
- `GET /errors/critical`, `PUT .../status` — admin-only, clean, paginated, no concerns (this is the endpoint I already fixed for the `size=0` crash earlier this session).

### `SupportController`

- `POST /support/submit` — **genuinely public, no auth, no dedicated anti-abuse control.** This is real validated input (`@Valid` + `@Size` caps, unlike `/errors/critical`), which is good — but **combined with the corrected rate-limiting finding above, this route currently has *zero* request-rate protection**, not even the 200/min/IP backstop originally assumed. Each submission fires 2 async Brevo emails (confirmation to submitter + notification to the team inbox). A scripted flood — trivially easy since there's no CAPTCHA, no per-IP cap, no email verification — could spam the team's support inbox and burn through Brevo's sending quota/reputation. **This is materially easier to abuse than `/auth/login`**, which at least gets a dedicated 5/min bucket. Recommend adding either a honeypot/CAPTCHA or wiring this into the `RateLimitInterceptor`'s path patterns before public launch.
- `GET /support/tickets`, `PUT .../status` — admin-only, clean, paginated.

### `NotificationController` (admin/editor in-app notifications — distinct from push campaigns below)

Staff-only throughout (`hasAnyRole('ADMIN','EDITOR')`), no end-user traffic, no scale concerns. One minor finding: `PUT /api/notifications/{id}/read` accepts the caller's email but never actually checks it against the notification's owner — any admin/editor could mark *any other* staff member's notification as read by guessing its UUID. Low severity (just a read-flag on a low-sensitivity in-app notice), but a real, fixable minor IDOR. The bulk `PUT /api/notifications/read-all` is correctly self-scoped via the SQL `WHERE` clause.

### `NotificationCampaignController` — **highest-priority finding in this entire report**

- **`POST /notification-campaigns` (create) can silently push a notification to the entire user base with zero confirmation, in both the frontend and the backend.** The admin "create campaign" form defaults to `targetType: 'ALL_USERS'` with no schedule date (`landing_page/src/pages/NotificationCampaignsPage.jsx:216`), and its submit handler has **no `window.confirm()`** — unlike the separate "Send Now" button for already-created campaigns, which does show a confirmation dialog. On the backend, `createCampaign()` immediately calls `processCampaign()` synchronously whenever `scheduledFor` is null (`NotificationCampaignServiceImpl.java:60-76`), resolving `ALL_USERS` via `userRepository.findAll()` and firing an async FCM push to every user with a token — **with no server-side safety check, dry-run, or staged-rollout option at all.** A single accidental click on the default create-form state is a one-click way to blast every device. `POST /notification-campaigns/{id}/send-now` has the *frontend* confirmation dialog the create route lacks, but still no backend-side check — meaning a direct API call (stolen token, script, future UI regression) bypasses the only safety net that exists today.
  - **Fix before launch:** add a confirmation step to the create form; add a backend-side safety check (e.g. require an explicit `confirm=true` flag or a recipient-count preview endpoint) before any `ALL_USERS` + immediate-send campaign can fire.
  - Secondary finding: `sentCount` is incremented based on "a token existed," not on whether the async FCM send actually succeeded (`NotificationCampaignServiceImpl.java:246-267`) — delivery stats are currently unreliable, especially if Firebase itself isn't configured (silent no-op, but still counted as sent).
  - Minor race: `cancel` and the `@Scheduled` `processScheduledCampaigns()` job (polls every 60s) both do read-then-write with no optimistic locking — a cancel issued in the same ~60s window a campaign is due could lose the race and send anyway. Low likelihood, worth a `@Version` field if this becomes operationally important.
- `POST /notification-campaigns/{id}/open` and `/click` — the mobile-side tracking beacons for push notifications. **Explicitly public** (correctly, since they fire before/without a session) — but per the corrected rate-limiting finding, they currently have **zero rate limiting**, not just the 200/min backstop originally assumed. Both do a non-atomic read-then-write counter increment (`findById` + `save` rather than a single `UPDATE ... SET x = x + 1`), which is a real lost-update race under the exact burst conditions a large campaign would create (many devices opening a push around the same moment) — low severity (cosmetic open/click-rate metric) but worth an atomic update fix.
- The rest of the controller (list/filter/edit/delete/cancel/analytics) is admin-only, clean, and correctly gated — `GET /{id}/analytics` appears redundant/unused (same data as plain `GET /{id}`).

### Summary of this slice's top findings

1. Push-campaign creation can blast the entire user base with zero confirmation (frontend or backend) — **highest-priority fix in the whole audit so far**.
2. `GET /api/kpi/global` has no role check at all — any logged-in user can read platform revenue/ARPU/LTV.
3. `GET /api/kpi/global` and `GET /api/analytics/firebase/detailed` both do unbounded `findAll()` + in-Java aggregation, and the former is polled every 30 seconds by the dashboard.
4. `/errors/critical` has no field-size validation despite accepting real device crash payloads.
5. `/support/submit` is public, unauthenticated, and — now that the rate-limiter scope is corrected — has **no** rate limiting at all, despite triggering 2 outbound emails per call.
6. GA4-backed analytics silently fall back to fabricated numbers (including permanently-hardcoded retention rates) whenever GA4 is unreachable or misconfigured, with no visual indication in the admin UI that the numbers aren't real.

---

## Routes — Misc (Root, Share, Well-Known)

### `GET /` — `RootController.java`

Public, zero-cost redirect to the Play Store, purely so anyone who hits the bare API domain doesn't see a raw error or any hint of the API surface. Always redirects to the **Android** Play Store URL regardless of visiting device — an iOS visitor hitting this directly would land on the wrong store page. Low priority (nothing appears to deliberately link here), but worth a platform-aware redirect if this URL is ever surfaced in marketing.

### `GET /share/recipes/{id}` — `ShareController.java`

Public, unauthenticated HTML page for social link previews (Open Graph/Twitter Card tags) plus a client-side deep-link redirect into the app. This is the page social crawlers (WhatsApp, iMessage, Telegram) fetch when a recipe is shared, and what `link.cookedapp.com/share/recipes/{id}` resolves to — confirmed as the canonical share-link host generated by `RecipeService.getShareLink()` and consumed by the mobile share sheet across multiple screens.

- Up to 3 sequential DB queries per request (recipe lookup + 2 lazy-loaded associations for cuisine/category), relying on `open-in-view` defaulting to `true` to even work.
- **No caching whatsoever** — no `@Cacheable`, no `Cache-Control`/ETag headers. A viral share spike on one recipe would re-run the same queries for every crawler and every human tap, with no rate limiting on this path either (it's not one of the 4 paths `RateLimitInterceptor` is wired to).
- Correctly returns a 404 fallback for missing or non-public recipes with no metadata leakage.
- Functionally coupled to `WellKnownController`'s Apple Universal Links file, which whitelists exactly this path.
- **Recommendation:** add a short-TTL cache or `Cache-Control` header around the recipe lookup, and replace the reliance on `open-in-view` with an explicit fetch-join query for correctness and to collapse 3 queries into 1.

### `GET /.well-known/assetlinks.json` and `GET /.well-known/apple-app-site-association` — `WellKnownController.java`

Static, public, near-zero-cost JSON files required for Android App Links / iOS Universal Links verification — fetched by the OS itself, not by any Cooked client code. Both correctly `permitAll`'d and content-typed.

- `assetlinks.json`'s SHA-256 fingerprint property defaults to a placeholder all-zeros value if `app.android.sha256` isn't set — if that's ever missing in production, Android link verification silently degrades to a disambiguation dialog instead of opening the app directly, with no startup warning to catch it. Worth a sanity check at boot.
- The Apple file's allowed-paths list only covers `/share/recipes/*` — any future universal-link surface (cookbooks, profiles, etc.) needs this file updated too, and it's easy to forget since it lives disconnected from the routes it's authorizing.

---

## External AI Service Integration (Markhor)

The core AI-powered features (ingredient scanning, recipe generation from ingredients or a photo, recipe-link extraction, trending suggestions) all depend on a single external vendor: **`recipe.markhorsystems.com`**, called via a plain `RestTemplate` with a custom `X-Internal-Secret` header for auth. This is confirmed to be a genuinely separate, externally-hosted service — there is no `recipe_generate_ai`/`ai_recipe` directory anywhere in this monorepo, and no local documentation of that vendor's own implementation. This is worth stating plainly: **a core, frequently-used product feature is fully dependent on infrastructure this team doesn't control or have visibility into.**

**Timeouts:** the shared `RestTemplate` bean is configured with a 10-second connect timeout and a **120-second read timeout** (`config/AppConfig.java`, explicitly commented "for AI"). Worst case, a single scan/generate call can hold a Tomcat request thread for **up to ~130 seconds**.

**Reliability:** there is no retry logic and no circuit breaker anywhere in the codebase (confirmed via dependency and code search — no resilience4j, no spring-retry). Each AI call is a single, synchronous, fire-and-forget attempt.

**Failure handling, route by route:**
- Onboarding suggestions and trending dishes: **graceful** — fall back to curated DB queries or a static list on any failure, no error surfaced.
- Recipe-link extraction: **graceful** — falls back to local jsoup HTML scraping if the AI call fails.
- Ingredient scan / typed-ingredient scan (the core, highest-traffic AI features): **clean, mapped errors** — specific HTTP status codes from the AI service are translated into friendly `BadRequestException`s (422 → "couldn't detect ingredients", 429 → "too many requests", 5xx → "temporarily unavailable"). No hangs, no raw 500s for these.
- Ingredient→recipe generation (`generateRecipes`): **weaker** — on failure, silently returns an empty list with a 200 rather than a 4xx, an inconsistent error contract compared to scan/scanTyped.
- **In every case**, the calling thread still blocks for however long the AI service takes to respond (up to the 130s ceiling) before any of the above fallback/error logic runs — the *error handling* is clean, but nothing makes the *wait* itself fast. The mobile client doesn't set an explicit shorter timeout on these specific calls either, so a slow AI backend shows up to the user as a long-hanging loading spinner, not a fast failure.

**At scale (~5,000 concurrent users):** this is the most likely single point of saturation in the backend. There is **no application-level throttling on any AI-backed route** — the rate limiter is wired to only 4 auth paths, not `/recipes/**`. Each in-flight AI call ties up a full Tomcat thread (default pool: 200) for up to ~130 seconds; back-of-envelope, that ceiling is reached at a fairly modest number of *simultaneous* scan/generate requests, well below 5,000 concurrent app users if even a modest fraction are actively scanning/generating at once. When that ceiling is hit, it degrades **every other endpoint on the same server**, not just the AI ones, since they share the same thread pool. On top of the AI latency itself, a *successful* response triggers `assignBestMatchingImages()`, which can issue up to ~4 sequential DB queries **per generated recipe** (up to ~24 extra queries for a 6-recipe response) against the same 20-connection Hikari pool — compounding AI latency with DB contention right after.

There is also a second, entirely unused `AiService` implementation (`AiServiceImpl.java`) that calls OpenAI directly — dead code today since `MarkhorAiServiceImpl` is `@Primary`, but worth removing or clearly marking to avoid future confusion about which AI backend is actually live.

**Recommendations for this specific risk:**
1. Add a bounded semaphore or a dedicated, smaller thread pool for AI-backed routes so a flood of scan/generate requests can't starve the rest of the API.
2. Shorten the read timeout where possible, or make these calls asynchronous (return a job ID, poll/push the result) so a slow AI response doesn't hold a request thread for 2 minutes.
3. Add basic retry-with-backoff for transient AI failures (5xx/timeouts) before falling back to the degraded-but-working paths that already exist for suggestions/trending.
4. Get real numbers from Markhor Systems on their own rate limits/capacity before launch — this backend currently has no way to detect or back off from vendor-side throttling.

---

## Cross-Cutting Bugs Found During This Audit

Collected here regardless of which route group surfaced them, since several apply app-wide:

1. **`JwtAuthenticationFilter` can hang a request** if a validly-signed JWT has a null `sub` claim (`security/JwtAuthenticationFilter.java:70`) — `filterChain.doFilter()` is unreachable in that specific path. See Infrastructure section.
2. **JWT signing secret has a hardcoded fallback in committed source** (`application.properties:28`) — verify `JWT_SECRET` is actually set in production; consider failing fast at startup if it isn't.
3. **Two conflicting CORS configurations** (`SecurityConfig` vs `WebConfig`) — remove the wildcard one in `WebConfig`.
4. **Rate limiting covers only 4 of 169 routes** despite code comments implying a global policy — the AI endpoints and `/support/submit` in particular are worth adding coverage for before a public launch.
5. **Three separate `new RestTemplate()` instances with no timeout configured** (`InstacartServiceImpl.java`, `KeepAliveTask.java`, `SubscriptionServiceImpl.java`) — none of these get the `AppConfig` bean's 10s/120s timeouts; a hang on any of these calls blocks a thread indefinitely.
6. **`SubscriptionRequiredFilter` and `JwtAuthenticationFilter` independently look up the same user by email** on every authenticated request — a small, but literally-every-request, redundant DB query.
7. **`GET /api/admin/categories` missing `@PreAuthorize`** while its siblings correctly require `ADMIN`/`EDITOR`.
8. **Client-side request-amplification patterns**: the mobile app fans out N individual HTTP calls instead of batching in at least two places found so far (grocery-item bulk-add, grocery-item auto-cleanup delete) — a `BatchCreateGroceryItemRequest` DTO already exists server-side but has no endpoint wired to it.
9. **N+1 query patterns** in `CookbookController` (both create and — critically — the update path used for every single add/remove-recipe interaction) and `MealPlanController`'s grocery-item creation, all traceable to per-ID `findById()` loops instead of `findAllById()`, and missing `@BatchSize` on several `Recipe`-adjacent lazy associations.
10. **`MealPlanController` appears entirely unconsumed** by any client — confirm with product/mobile whether this is a planned-but-unshipped feature or genuinely dead code.
11. **`POST /grocery-items/instacart` is unused and has an unbounded external-call timeout** — low priority only *because* it's unused; fix the timeout before ever wiring it up client-side.
12. **JWTs never expire, and the `jwt.expiration` property that suggests otherwise is dead configuration** — `JwtService.java:44-46` builds tokens with no `.setExpiration(...)` call at all; nothing in the codebase reads `jwt.expiration`/`JWT_EXPIRATION`. There is also no refresh-token endpoint anywhere. See Infrastructure and Auth sections.
13. **`JwtService.isTokenValid()` re-checks the blacklist a second time**, duplicating the check `JwtAuthenticationFilter` already just performed — a second redundant query stacked on top of finding #6, on every authenticated request.
14. **`DeviceSession.token` has no unique/index constraint** (unlike `BlacklistedToken.token`, which is indexed) — `/auth/logout`'s `findByToken` will degrade toward a table scan as `device_sessions` grows.

---

## Prioritized Recommendations Before Launch

This ranks every finding in the report by actual launch risk, not by where it happened to be found. Tier P0 items are genuine vulnerabilities or data-loss vectors reachable today by any user (or, in a few cases, any anonymous caller) — fix these before a public launch. Tier P1 items are the scale/reliability risks most likely to actually bite at real concurrency (the "5,000 simultaneous clients" question this audit was specifically asked to answer). Tier P2 is correctness/cleanup debt worth scheduling but not blocking.

### P0 — Fix before launch (all are targeted, hours-not-weeks fixes; none require an architecture change)

1. **`RecipeDataController` (`/api/recipe-data/**`) has zero authentication on any route.** Add `@PreAuthorize("hasAnyRole('ADMIN','EDITOR')")` across the controller. This is the single highest-priority item in the report: as-is, anyone on the internet can trigger billed OpenAI DALL·E calls, hard-delete the entire catalog, and overwrite arbitrary live recipe images.
2. **`POST /admin/seed/explore/reset` can permanently wipe the public Explore catalog with no role check and no working reseed.** Add `@PreAuthorize("hasRole('ADMIN')")` to it and its sibling `POST /admin/seed/explore`; fix or remove the dead-code reseed path (`ExploreDataSeederServiceImpl.java:31-33`) so a reset doesn't leave the catalog empty.
3. **Verify `REVENUECAT_WEBHOOK_SECRET` is actually set in the production Render environment**, and change `RevenueCatWebhookController` to fail closed (401) rather than silently accepting all requests when the secret is unconfigured.
4. **`POST /webhooks/apple` never verifies the Apple JWS signature** — implement real signature verification against Apple's published certificates, or at minimum treat the webhook as advisory and re-verify via `verify-receipt`/the App Store Server API before granting entitlements from it.
5. **`POST /user/sync-subscription` trusts an arbitrary client-supplied body to grant premium status.** Remove it if the RevenueCat webhook + `/subscriptions/verify-receipt` already cover the legitimate case, or make it log-only/advisory rather than authoritative.
6. **`GET /recipes/{id}/share` has no ownership check** and will publicize any recipe by ID. Add the same `recipe.getUser().getEmail().equals(userEmail)` check already used by every sibling mutation in `RecipeController`.
7. **`POST /auth/verify-reset-code` has no rate limiting or lockout**, making the password-reset OTP brute-forceable for both end-user and admin accounts. Add it to `RateLimitInterceptor`'s path list and/or give it the same lockout pattern already used by `resendCode`/`forgotPassword`.
8. **`/recipes/import`'s server-side URL fetch has no SSRF protection.** Add a private-IP/localhost/link-local blocklist before the Jsoup fetch runs.
9. **Confirm `JWT_SECRET` is actually set in production** (the hardcoded fallback in `application.properties:28` is visible to anyone with repo access) — this is especially important given finding #12 below (tokens never expire, so a forged token from a leaked default secret would be permanent).
10. **`POST /subscriptions/pay` is mocked/test code that grants a real subscription for a fake payment token**, with a `Thread.sleep(1000)` inside an open transaction. It appears unreachable from the shipped mobile UI, but the HTTP endpoint is still live — make a product decision (finish the real Stripe/IAP integration or remove/gate the route) before launch, since as-is it's both a monetization bypass and a self-inflicted DoS lever.

### P1 — Fix soon; these are the routes most likely to actually cause problems at real concurrency

11. **The shared 20-connection Hikari pool is the app's real ceiling, and several routes hold a connection for the duration of an external call that can legally take up to 120 seconds**: `POST /recipes/generate-ai-recipes` and `POST /recipes/import` (both `@Transactional` around the AI call), `POST /subscriptions/verify-receipt` (raw, un-timed `RestTemplate`, also `@Transactional`), and `POST /admin/seed/image-library-backfill` (effectively unbounded, one call per untagged image). At even modest concurrent load on any of these, the pool can saturate and start blocking every other route in the app. Prioritize decoupling the external call from the DB transaction (fetch/call first, persist in a short transaction after) on the two highest-traffic ones (`generate-ai-recipes`, `import`).
12. **Every authenticated request pays 3-4 redundant DB round trips** before reaching a controller (JWT filter's user lookup + blacklist check, a second duplicate blacklist check inside `JwtService`, and `SubscriptionRequiredFilter`'s independent re-lookup of the same user) — `GET /user/me`, likely the single highest-traffic route in the app, triples this cost by recomputing the same subscription check a third time. This is the biggest generic scale risk in the report; the cheapest fix is a short-TTL (few-second) in-memory cache keyed by email/JWT shared across the filter chain and `/user/me`.
13. **Rate limiting covers only 4 of 169 routes** (`/auth/login`, `/auth/forgot-password`, `/auth/resend-code`, `/auth/verify-email`) despite code comments implying a global 200/min backstop. At minimum, add coverage for `/support/submit` (public, triggers 2 emails per call, no CAPTCHA), `/auth/register`, `/auth/verify-reset-code` (see P0 #7), and the AI-backed `/recipes/*` routes before a public launch.
14. **`POST /notification-campaigns` can blast a push notification to the entire user base with zero confirmation, in both the admin frontend and the backend.** Add a confirmation step to the create form (the separate "Send Now" button already has one) and a backend-side safety check before any `ALL_USERS` + immediate-send campaign fires.
15. **`GET /api/kpi/global` (the admin dashboard's revenue/ARPU/LTV widget, polled every 30 seconds) has no `@PreAuthorize` at all** — any authenticated user, not just admins, can read platform-wide revenue data. Add `@PreAuthorize("hasRole('ADMIN')")` and replace its unbounded `findAll()` + in-Java aggregation with SQL `SUM`/`GROUP BY`.
16. **`GET /subscriptions/paywall-config`** (public, hit on every paywall render) **writes to the database on every single call** via a fully redundant `save()` of static copy — pure write amplification against the shared pool at exactly the moment (a paywall spike) load is highest. Make it read-only.
17. **`GET /api/admin/recipes/leaderboard`** is an `O(6×editor-count)` N+1, and a redundant per-row `findById` in `RecipeAssignmentServiceImpl.mapToResponse` is duplicated across three other paginated list endpoints — the two clearest N+1 patterns in the whole audit. Both have straightforward single-query fixes described in the relevant route sections.
18. **`CookbookController`'s `PUT /cookbooks/{id}`** — the endpoint the mobile app actually uses for every single "save recipe to cookbook" tap — has a stacked double N+1 (per-ID `findById` loop + unbatched lazy associations) scaled by the cookbook's *total* recipe count, not just the one recipe being added/removed. Given how frequent this interaction likely is, this is worth fixing alongside the AI-route connection-holding issues in #11.

### P2 — Worth scheduling, not launch-blocking

19. Two conflicting CORS configurations (`SecurityConfig` vs `WebConfig`) — delete the wildcard one in `WebConfig`.
20. `JwtAuthenticationFilter` can hang a request (never calls `doFilter`) on a validly-signed JWT with a null `sub` claim — low likelihood, real bug.
21. Confirm `Status.BLOCKED`/`ARCHIVED` actually disables an already-issued token (via `User`'s `UserDetails` methods) — otherwise the admin "block user" action doesn't revoke existing sessions, which matters more than usual given tokens never expire.
22. Dead code worth removing for clarity: the unused `AiServiceImpl` (direct OpenAI path, `@Primary` never applies to it), `GET /subscriptions/status`, `PUT /recipes/{id}/visibility`, and the handful of confirmed-orphaned `RecipeAssignmentController`/`SavedIngredientController` routes listed in their respective sections.
23. Add DB indexes flagged across the report: `device_sessions.token`, `users.role`, `recipe_assignments(status)` / `(assigned_to_user_id, status)`.
24. GA4-backed analytics (`GET /api/analytics/firebase/*`) silently fall back to fabricated numbers — including permanently-hardcoded retention rates — when GA4 is unreachable or misconfigured, with no visual indicator in the admin UI. Worth at least a "data may be estimated" flag client-side.
25. Get real capacity/rate-limit numbers from Markhor Systems (the external AI vendor) before launch — this backend has no way today to detect or back off from vendor-side throttling, and the AI routes are already the most likely saturation point per the dedicated section above.
26. Once the P0 items are fixed, consider a real load test against a **staging** copy of the backend (explicitly not production — see Method note at the top of this report) to replace the engineering-judgment response-time estimates in this report with measured numbers, particularly for the AI routes and `/user/me`.

---

*This report was generated by a static-code audit (no live load testing was performed against the production backend). Treat response-time and scale estimates as engineering judgment to guide where to focus real load testing, not as measured guarantees.*
