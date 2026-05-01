'use strict';

// Node.js v24 on Windows can incorrectly resolve DNS via 127.0.0.1 (c-ares bug).
// Force reliable public DNS before any network calls are made.
require('dns').setServers(['8.8.8.8', '8.8.4.4']);

const fs = require('fs');
const os = require('os');
const path = require('path');

const { createApp } = require('./app');
const config = require('./config/index');
const logger = require('./utils/logger');

const PID_FILE = path.join(os.tmpdir(), 'ai-recipe-api.pid');

function isProcessAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (_err) {
    return false;
  }
}

function terminatePreviousInstance() {
  try {
    if (!fs.existsSync(PID_FILE)) return;

    const previousPid = Number.parseInt(fs.readFileSync(PID_FILE, 'utf8').trim(), 10);
    if (!Number.isInteger(previousPid) || previousPid <= 0 || previousPid === process.pid) return;
    if (!isProcessAlive(previousPid)) return;

    try {
      if (process.platform === 'win32') {
        require('child_process').execFileSync('taskkill', ['/PID', String(previousPid), '/T', '/F'], {
          stdio: 'ignore',
        });
      } else {
        process.kill(previousPid, 'SIGTERM');
      }
      logger.warn(`Stopped previous app instance on port ${config.port} (pid ${previousPid})`);
    } catch (err) {
      logger.warn(`Could not stop previous app instance (pid ${previousPid}): ${err.message}`);
    }
  } catch (err) {
    logger.warn(`PID handoff check failed: ${err.message}`);
  }
}

function writeCurrentPid() {
  try {
    fs.writeFileSync(PID_FILE, String(process.pid), 'utf8');
  } catch (err) {
    logger.warn(`Could not write PID file: ${err.message}`);
  }
}

function removePidFile() {
  try {
    if (fs.existsSync(PID_FILE)) {
      fs.unlinkSync(PID_FILE);
    }
  } catch (err) {
    logger.warn(`Could not remove PID file: ${err.message}`);
  }
}

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
    terminatePreviousInstance();

    // 1. Create and configure Express app
    const app = createApp();

    // 4. Start HTTP server
    const server = app.listen(config.port, () => {
      writeCurrentPid();
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
          removePidFile();
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
      removePidFile();
      process.exit(1);
    });

    return server;
  } catch (err) {
    logger.error('Fatal startup error:', err);
    process.exit(1);
  }
}

start();
