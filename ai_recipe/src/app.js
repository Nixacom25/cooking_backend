'use strict';

const express = require('express');
const helmet = require('helmet');
const compression = require('compression');
const swaggerUi = require('swagger-ui-express');

const config = require('./config/index');
const { swaggerSpec } = require('./config/swagger');
const logger = require('./utils/logger');
const { errorHandler } = require('./middleware/errorHandler');
const { apiRateLimiter } = require('./middleware/rateLimiter');
const { requestContext } = require('./middleware/requestContext');
const { requestLogger } = require('./middleware/requestLogger');


const recipeRoutes = require('./routes/recipeRoutes');
const analyzeRoutes = require('./routes/analyzeRoutes');
const userRoutes = require('./routes/userRoutes');
const extractRoutes = require('./routes/extractRoutes');
const imageRoutes = require('./routes/imageRoutes');
const ingredientRoutes = require('./routes/ingredientRoutes');

/**
 * Creates and configures the Express application.
 * Separated from server.js so the app can be imported in tests without
 * binding to a port.
 */
function createApp() {
  const app = express();

  // Behind a reverse proxy (Coolify, Nginx, etc.) so that rate limiting and
  // other middleware relying on client IP can safely use X-Forwarded-*.
  app.set('trust proxy', 1);

  // ------------------------------------------------------------------
  // Request context and request lifecycle logs
  // ------------------------------------------------------------------
  app.use(requestContext);
  app.use(requestLogger);

  // ------------------------------------------------------------------
  // Security headers (helmet.js)
  // ------------------------------------------------------------------
  app.use(
    helmet({
      crossOriginResourcePolicy: { policy: 'cross-origin' }, // Allow mobile apps to load images
      crossOriginOpenerPolicy: false, // Avoid COOP warnings on non-HTTPS/cloud preview URLs
      originAgentCluster: false, // Paired with COOP; disable to reduce noisy logs
    })
  );

  // ------------------------------------------------------------------
  // CORS — allow all origins (public API)
  // ------------------------------------------------------------------
  app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Request-Id');
    res.setHeader('Access-Control-Expose-Headers', 'X-Request-Id, RateLimit-Limit, RateLimit-Remaining, RateLimit-Reset');
    res.setHeader('Access-Control-Max-Age', '86400'); // 24h preflight cache

    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
  });

  // ------------------------------------------------------------------
  // Request body parsers
  // ------------------------------------------------------------------
  app.use(express.json({ limit: '20mb' }));       // JSON bodies (high limit for Base64 image payloads)
  app.use(express.urlencoded({ extended: false })); // URL-encoded form data

  // ------------------------------------------------------------------
  // HTTP compression (gzip)
  // ------------------------------------------------------------------
  app.use(compression());

  // ------------------------------------------------------------------
  // Global rate limiter removed — apply per route to avoid middleware clashes
  // ------------------------------------------------------------------

  // ------------------------------------------------------------------
  // Health check (unauthenticated, for load balancers / uptime monitors)
  // ------------------------------------------------------------------
  app.get('/health', (_req, res) => {
    res.status(200).json({
      status: 'ok',
      env: config.env,
      timestamp: new Date().toISOString(),
    });
  });

  // ------------------------------------------------------------------
  // Root health check (for Render default ping)
  // ------------------------------------------------------------------
  app.get('/', (_req, res) => {
    res.status(200).json({ status: 'ok', service: 'ai-recipe-service' });
  });

  app.head('/', (_req, res) => {
    res.status(200).end();
  });

  // ------------------------------------------------------------------
  // API routes
  // (aiEndpointLimiter is securely applied directly inside these modules)
  // ------------------------------------------------------------------

  app.use('/api/recipes', recipeRoutes);
  app.use('/api/analyze', analyzeRoutes);
  app.use('/api/users', userRoutes);
  app.use('/api/extract', extractRoutes);
  app.use('/api/images', imageRoutes);
  app.use('/api/ingredients', ingredientRoutes);

  // ------------------------------------------------------------------
  // API documentation — Swagger UI + raw OpenAPI JSON
  // Only served outside production to avoid exposing internals publicly.
  // To enable in production set ENABLE_DOCS=true in .env.
  // ------------------------------------------------------------------
  if (config.env !== 'production' || process.env.ENABLE_DOCS === 'true') {
    // Raw OpenAPI spec (useful for code-generation tools and CI checks)
    app.get('/api/docs.json', apiRateLimiter, (_req, res) => {
      res.setHeader('Content-Type', 'application/json');
      res.send(swaggerSpec);
    });

    // Interactive Swagger UI (served from this origin so it complies with
    // strict Content Security Policy headers like `script-src 'self'` that
    // some hosting environments inject by default).
    app.use(
      '/api/docs',
      apiRateLimiter,
      swaggerUi.serve,
      swaggerUi.setup(swaggerSpec, {
        customSiteTitle: 'AI Powered Chef — API Docs',
        swaggerOptions: {
          defaultModelsExpandDepth: 1,   // expand schemas one level by default
          defaultModelExpandDepth: 1,
          docExpansion: 'list',          // endpoints collapsed, tags expanded
          filter: true,                  // enable search bar
          tryItOutEnabled: false,        // keep Try-it-out opt-in (avoids accidental AI calls)
        },
      })
    );

    logger.info(`API docs: http://localhost:${config.port}/api/docs`);
  }

  // ------------------------------------------------------------------
  // 404 handler for unknown routes
  // ------------------------------------------------------------------
  app.use((_req, res) => {
    res.status(404).json({
      success: false,
      error: {
        code: 'NOT_FOUND',
        message: 'The requested endpoint does not exist',
      },
    });
  });

  // ------------------------------------------------------------------
  // Global error handler (must be last)
  // ------------------------------------------------------------------
  app.use(errorHandler);

  return app;
}

module.exports = { createApp };
