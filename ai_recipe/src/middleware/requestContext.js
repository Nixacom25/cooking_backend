'use strict';

const crypto = require('crypto');
const logger = require('../utils/logger');

function requestContext(req, res, next) {
    const incoming = req.get('x-request-id');
    const requestId = incoming && incoming.trim() ? incoming.trim() : crypto.randomUUID();

    res.setHeader('X-Request-Id', requestId);

    const context = {
        request_id: requestId,
        method: req.method,
        path: req.originalUrl || req.url,
        ip: req.ip,
    };

    req.request_id = requestId;
    logger.enterRequestContext(context);

    next();
}

module.exports = { requestContext };
