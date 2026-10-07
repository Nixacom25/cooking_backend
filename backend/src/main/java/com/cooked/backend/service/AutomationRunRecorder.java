package com.cooked.backend.service;

/** Records a scheduled job run (async, never throws). */
public interface AutomationRunRecorder {
    void record(String job, boolean success, int durationMs, String detail);
}
