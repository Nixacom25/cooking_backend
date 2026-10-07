package com.cooked.backend.service;

import com.cooked.backend.entity.CriticalError;

/** Tells the team about a critical app error, through the channels enabled in Settings. */
public interface CriticalErrorNotifier {
    void notify(CriticalError error, String shortId);
}
