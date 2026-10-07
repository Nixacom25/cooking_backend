package com.cooked.backend.service;

import com.cooked.backend.entity.IntegrationKey;

/** Records the outcome of an exchange with an external service (async, never throws). */
public interface IntegrationEventRecorder {

    void record(IntegrationKey integration, String name, boolean success, Integer httpStatus, Integer latencyMs, String detail);
}
