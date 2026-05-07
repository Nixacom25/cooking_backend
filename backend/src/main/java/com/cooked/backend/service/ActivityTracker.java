package com.cooked.backend.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ActivityTracker {
    private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());

    public void updateActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }

    public long getLastActivityTime() {
        return lastActivityTime.get();
    }

    public boolean hasBeenInactiveFor(long milliseconds) {
        return (System.currentTimeMillis() - lastActivityTime.get()) >= milliseconds;
    }
}
