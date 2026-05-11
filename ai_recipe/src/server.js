'use strict';

// Node.js v24 on Windows can incorrectly resolve DNS via 127.0.0.1 (c-ares bug).
// Force reliable public DNS before any network calls are made.
require('dns').setServers(['8.8.8.8', '8.8.4.4']);

const { createApp } = require('./app');
const config = require('./config/index');
const logger = require('./utils/logger');

/**
 * Application entry point.
 *
 * Startup sequence:
 * 1. Connect to MongoDB
 * 2. Initialise Redis client (lazy — connection happens on first use)
 * 3. Create Express app
 * 4. Start HTTP server
 *
 * Shutdown sequence (SIGTERM/SIGINT):
 * 1. Stop accepting new connections
 * 2. Close MongoDB connection
 * 3. Close Redis connection
 */
async function start() {
  try {
    // 1. Create and configure Express app
    const app = createApp();

    // 4. Start HTTP server
    const server = app.listen(config.port, () => {
      logger.info(
        `🚀 AI Recipe API server running on port ${config.port} [${config.env}]`
      );
      logger.info(`Health check: http://localhost:${config.port}/health`);
    });

    const openSockets = new Set();
    server.on('connection', (socket) => {
      openSockets.add(socket);
      socket.on('close', () => openSockets.delete(socket));
    });

    // Keep request timeouts configurable for slow cold starts and AI latency spikes.
    server.requestTimeout = config.httpTimeoutMs;
    server.timeout = config.httpTimeoutMs;
    server.headersTimeout = config.httpTimeoutMs + 5000;

    // ------------------------------------------------------------------
    // Graceful shutdown handlers
    // ------------------------------------------------------------------
    const shutdown = async (signal) => {
      logger.info(`${signal} received — initiating graceful shutdown...`);

      // Force-close any keep-alive sockets so watch restarts release the port immediately.
      for (const socket of openSockets) {
        try {
          socket.destroy();
        } catch (_err) {
          // Ignore socket teardown errors during shutdown.
        }
      }

      server.close(async () => {
        logger.info('HTTP server closed');
        try {
          logger.info('Exiting');
          process.exit(0);
        } catch (err) {
          logger.error('Error during shutdown:', err);
          process.exit(1);
        }
      });

      // Force exit if shutdown takes too long (15s)
      setTimeout(() => {
        logger.error('Graceful shutdown timed out — forcing exit');
        process.exit(1);
      }, 15000);
    };

    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));

    // ------------------------------------------------------------------
    // Unhandled rejection safety net
    // ------------------------------------------------------------------
    process.on('unhandledRejection', (reason) => {
      logger.error('Unhandled promise rejection:', reason);
    });

    process.on('uncaughtException', (err) => {
      logger.error('Uncaught exception:', err);
      process.exit(1);
    });

    return server;
  } catch (err) {
    logger.error('Fatal startup error:', err);
    process.exit(1);
  }
}

start();
