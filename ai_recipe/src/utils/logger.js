'use strict';

const { AsyncLocalStorage } = require('async_hooks');
const util = require('util');
const winston = require('winston');
const config = require('../config/index');

const requestContextStore = new AsyncLocalStorage();
const ANSI = {
  reset: '\x1b[0m',
  gray: '\x1b[90m',
  blue: '\x1b[34m',
  red: '\x1b[31m',
};

const REDACT_KEYS = new Set([
  'authorization',
  'api_key',
  'apikey',
  'openai_api_key',
  'password',
  'token',
  'secret',
  'image_base64',
]);

function getRequestContext() {
  return requestContextStore.getStore() || null;
}

function withRequestContext(context, callback) {
  return requestContextStore.run(context, callback);
}

function enterRequestContext(context) {
  requestContextStore.enterWith(context);
}

function redact(value, depth = 0) {
  if (value == null || depth > 4) return value;
  if (typeof value === 'string') {
    if (value.length > 256 && value.includes('base64')) return '[REDACTED_BASE64]';
    return value;
  }
  if (Array.isArray(value)) return value.slice(0, 20).map((item) => redact(item, depth + 1));
  if (typeof value === 'object') {
    const out = {};
    for (const [k, v] of Object.entries(value)) {
      const key = String(k).toLowerCase();
      out[k] = REDACT_KEYS.has(key) ? '[REDACTED]' : redact(v, depth + 1);
    }
    return out;
  }
  return value;
}

function detailsToString(details, useColors = false) {
  if (details == null) return '';
  if (typeof details === 'string') return details;
  return util.inspect(details, {
    colors: useColors,
    depth: 5,
    breakLength: 100,
    compact: false,
    sorted: true,
  });
}

function indentLines(text, spaces = 2) {
  const prefix = ' '.repeat(spaces);
  return String(text)
    .split('\n')
    .map((line) => `${prefix}${line}`)
    .join('\n');
}

function color(text, code) {
  if (!text) return text;
  return `${code}${text}${ANSI.reset}`;
}

winston.addColors({
  error: 'red',
  warn: 'yellow',
  info: 'green',
  http: 'cyan',
  debug: 'grey',
});

const injectRequestContext = winston.format((info) => {
  const ctx = getRequestContext();
  if (ctx && !info.request_id) {
    info.request_id = ctx.request_id;
    info.method = info.method || ctx.method;
    info.path = info.path || ctx.path;
    info.ip = info.ip || ctx.ip;
  }
  if (typeof info.message === 'object') {
    info.details = redact(info.message);
    info.message = info.event || 'log';
  }
  if (info.details) {
    info.details = redact(info.details);
  }
  return info;
});

/**
 * Application-wide Winston logger.
 * Outputs JSON in production, pretty-printed in development.
 * Stack traces are never forwarded to API clients — only internal logs.
 */
const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || (config.env === 'production' ? 'info' : 'debug'),
  format:
    config.env === 'production'
      ? winston.format.combine(
        injectRequestContext(),
        winston.format.splat(),
        winston.format.timestamp(),
        winston.format.errors({ stack: true }),
        winston.format.json()
      )
      : winston.format.combine(
        injectRequestContext(),
        winston.format.splat(),
        winston.format.colorize(),
        winston.format.timestamp({ format: 'HH:mm:ss' }),
        winston.format.errors({ stack: true }),
        winston.format.printf((info) => {
          const level = info.level;
          const message = info.message;
          const timestamp = info.timestamp;
          const isRequestLog = info.event === 'request.log';

          if (isRequestLog) {
            const req = (info.details && info.details.req) || {};
            const res = (info.details && info.details.res) || {};
            const method = info.method || req.method || '-';
            const path = info.path || req.path || '-';
            const header = `${color(timestamp, ANSI.gray)} [${level}] ${color(method, ANSI.blue)} ${color(path, ANSI.blue)}`;
            const reqBlock = indentLines(detailsToString(req, true), 2);
            const resBlock = indentLines(detailsToString(res, true), 2);

            const errorCode = res.response && res.response.error && res.response.error.code;
            const errorMessage = res.response && res.response.error && res.response.error.message;
            const errorLine = errorCode && errorMessage
              ? `\n${color(`[${errorCode}] ${errorMessage}`, ANSI.red)}`
              : '';

            return [
              '',
              '============================================================',
              header,
              'req:',
              reqBlock,
              errorLine ? `---${errorLine}` : '',
              'res:',
              resBlock,
              '============================================================',
            ]
              .filter(Boolean)
              .join('\n');
          }

          const line = `${timestamp} [${level}] ${message}`;
          const details = detailsToString(info.details, true);

          if (details) {
            return `${line}\ndetails:\n${indentLines(details, 2)}`;
          }

          if (info.stack) {
            return `${line}\n${info.stack}`;
          }

          return line;
        })
      ),
  transports: [new winston.transports.Console()],
});

logger.event = function event(level, eventName, message, details = {}) {
  logger.log(level, message || eventName, { event: eventName, details });
};

logger.withRequestContext = withRequestContext;
logger.enterRequestContext = enterRequestContext;
logger.getRequestContext = getRequestContext;

module.exports = logger;
