'use strict';

const logger = require('../utils/logger');

function summarizeResponseBody(body) {
    if (!body || typeof body !== 'object') return undefined;

    const summary = {};
    if (typeof body.success === 'boolean') summary.success = body.success;
    if (Array.isArray(body.recipes)) summary.recipes = body.recipes.length;
    if (Array.isArray(body.allowed_ingredients)) summary.allowed_ingredients = body.allowed_ingredients.length;
    if (Array.isArray(body.restricted_ingredients)) summary.restricted_ingredients = body.restricted_ingredients.length;
    if (body.error && typeof body.error === 'object') summary.error_code = body.error.code;

    return Object.keys(summary).length > 0 ? summary : undefined;
}

function requestLogger(req, res, next) {
    const startedAt = process.hrtime.bigint();
    let responseBody;

    const originalJson = res.json.bind(res);
    res.json = (body) => {
        responseBody = body;
        return originalJson(body);
    };

    res.on('finish', () => {
        const durationMs = Number(process.hrtime.bigint() - startedAt) / 1e6;
        const path = req.originalUrl || req.url;
        logger.event('info', 'request.log', 'API request completed', {
            method: req.method,
            path,
            req: {
                req: req.request_id,
                ip: req.ip,
                method: req.method,
                path,
            },
            res: {
                duration_ms: Number(durationMs.toFixed(2)),
                status_code: res.statusCode,
                response: responseBody !== undefined ? responseBody : summarizeResponseBody(res.locals),
            },
        });
    });

    next();
}

module.exports = { requestLogger };
